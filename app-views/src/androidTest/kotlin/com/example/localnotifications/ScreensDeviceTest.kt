package com.example.localnotifications

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.devicetesting.allowingNotificationPermission
import com.example.localnotifications.core.devicetesting.appContext
import com.example.localnotifications.core.devicetesting.bringToFront
import com.example.localnotifications.core.devicetesting.device
import com.example.localnotifications.core.devicetesting.passes
import com.example.localnotifications.core.devicetesting.reopenApp
import com.example.localnotifications.core.devicetesting.waitFor
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.core.testing.FakeNotificationScheduler
import com.example.localnotifications.di.DataModule
import com.example.localnotifications.di.NotificationsModule
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.ui.detail.DetailActivity
import com.example.localnotifications.ui.list.MainActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screen behaviour that depends on the real system: physical rotation, leaving through the
 * launcher and coming back, real toolbar menus and dialogs. Data and scheduling are faked.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, NotificationsModule::class)
@RunWith(AndroidJUnit4::class)
class ScreensDeviceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val repository = FakeNotificationRepository()
    private val scheduler = FakeNotificationScheduler()

    @BindValue
    @JvmField
    val boundRepository: NotificationRepository = repository

    @BindValue
    @JvmField
    val boundScheduler: NotificationScheduler = scheduler

    private val items = listOf(NotificationItem(1, "First", 10), NotificationItem(2, "Second", 20))
    private var scenario: ActivityScenario<*>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.result = Result.success(items)
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
        device.unfreezeRotation()
        scenario?.close()
    }

    /**
     * After a rotation, waits until the recreated screen has window focus. Otherwise Espresso can
     * pick the old, destroyed window and wait for focus that never comes.
     */
    private fun ActivityScenario<MainActivity>.waitForRecreatedWindow() {
        device.waitForIdle()
        waitFor {
            var focused = false
            onActivity { focused = it.hasWindowFocus() }
            focused
        }
    }

    @Test
    fun rotatingTheDevice_keepsTheListWithoutReloading() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        onView(withText("First")).check(matches(isDisplayed()))

        device.setOrientationLeft()
        main.waitForRecreatedWindow()
        onView(withText("Second")).check(matches(isDisplayed()))

        device.setOrientationNatural()
        main.waitForRecreatedWindow()
        onView(withText("First")).check(matches(isDisplayed()))

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun leavingThroughHomeAndReopening_reloadsTheList() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        onView(withText("First")).check(matches(isDisplayed()))
        var before: MainActivity? = null
        main.onActivity { before = it }

        device.pressHome()
        waitFor { main.state == Lifecycle.State.CREATED }
        // The app counts as backgrounded ~700 ms after its last screen stops.
        waitFor { ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.CREATED }
        // Back to the same screen, as reopening from recents does.
        bringToFront(MainActivity::class.java)

        waitFor { repository.listCalls == 2 }
        main.onActivity { assertSame("Expected the same screen, not a new one", before, it) }
        onView(withText("First")).check(matches(isDisplayed()))
    }

    @Test
    fun openingDetailsAndGoingBack_doesNotReloadTheList() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        onView(withText("First")).perform(click())
        onView(withId(R.id.scheduleButton)).check(matches(isDisplayed()))

        device.pressBack()

        waitFor { main.state == Lifecycle.State.RESUMED }
        onView(withText("First")).check(matches(isDisplayed()))
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun leavingThroughHomeOnDetails_reloadsTheListWhenGoingBack() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        onView(withText("First")).perform(click())
        onView(withId(R.id.scheduleButton)).check(matches(isDisplayed()))

        device.pressHome()
        // The app counts as backgrounded ~700 ms after its last screen stops.
        waitFor { ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.CREATED }
        reopenApp()
        waitFor { passes { onView(withId(R.id.scheduleButton)).check(matches(isDisplayed())) } }
        assertEquals(1, repository.listCalls)

        device.pressBack()

        waitFor { main.state == Lifecycle.State.RESUMED }
        waitFor { repository.listCalls == 2 }
    }

    @Test
    fun cancelAllMenu_cancelsEverythingAndConfirms() {
        items.forEach(scheduler::schedule)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.action_cancel_all)).perform(click())

        assertTrue(scheduler.pending.isEmpty())
        onView(withText(R.string.all_cancelled)).check(matches(isDisplayed()))
    }

    @Test
    fun detail_cancellingTheConfirmation_schedulesNothing() {
        val intent = Intent(appContext, DetailActivity::class.java).putExtra(DetailArgs.NOTIFICATION_ID, 1)
        scenario = ActivityScenario.launch<DetailActivity>(intent)
        onView(withText("First")).check(matches(isDisplayed()))

        onView(withId(R.id.scheduleButton)).perform(click())
        onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())

        onView(withText(R.string.confirm_schedule_title)).check(doesNotExist())
        assertTrue(scheduler.pending.isEmpty())
        onView(withId(R.id.action_cancel)).check(matches(not(isEnabled())))
    }

    @Test
    fun detail_scheduleConfirms_andCancelMenuFollowsTheScheduledState() {
        val intent = Intent(appContext, DetailActivity::class.java).putExtra(DetailArgs.NOTIFICATION_ID, 1)
        scenario = ActivityScenario.launch<DetailActivity>(intent)
        onView(withText("First")).check(matches(isDisplayed()))
        onView(withId(R.id.action_cancel)).check(matches(not(isEnabled())))

        onView(withId(R.id.scheduleButton)).perform(click())
        onView(withText(R.string.confirm_schedule_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        assertTrue(scheduler.pending.isEmpty())
        allowingNotificationPermission {
            onView(withText(R.string.action_schedule)).inRoot(isDialog()).perform(click())
        }

        onView(withText(R.string.scheduled_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
        assertEquals(items[0], scheduler.pending[1])
        onView(withId(R.id.action_cancel)).check(matches(isEnabled()))

        onView(withId(R.id.action_cancel)).perform(click())

        assertTrue(scheduler.pending.isEmpty())
        onView(withText(R.string.notification_cancelled)).check(matches(isDisplayed()))
        onView(withId(R.id.action_cancel)).check(matches(not(isEnabled())))
    }
}
