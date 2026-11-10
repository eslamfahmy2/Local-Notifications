package com.example.localnotifications

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.notifications.scheduler.WorkManagerNotificationScheduler
import com.example.localnotifications.core.devicetesting.allowingNotificationPermission
import com.example.localnotifications.core.devicetesting.appContext
import com.example.localnotifications.core.devicetesting.device
import com.example.localnotifications.core.devicetesting.passes
import com.example.localnotifications.core.devicetesting.waitFor
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.di.DataModule
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.ui.detail.DetailActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real scheduling on the device: real WorkManager, real worker, real notification in the shade.
 * Only the feed is faked, with short delays so the tests stay quick.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class)
@RunWith(AndroidJUnit4::class)
class NotificationDeviceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val repository = FakeNotificationRepository()

    @BindValue
    @JvmField
    val boundRepository: NotificationRepository = repository

    private val item = NotificationItem(7, "Device test notification", 3)
    private var scenario: ActivityScenario<DetailActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.result = Result.success(listOf(item))
        clearNotifications()
    }

    @After
    fun tearDown() {
        device.pressBack() // closes the shade if a test left it open
        scenario?.close()
        clearNotifications()
    }

    private fun clearNotifications() {
        WorkManager.getInstance(appContext).cancelAllWork()
        appContext.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun openDetailAndSchedule() {
        val intent = Intent(appContext, DetailActivity::class.java).putExtra(DetailArgs.NOTIFICATION_ID, item.id)
        scenario = ActivityScenario.launch<DetailActivity>(intent)
        onView(withText(item.title)).check(matches(isDisplayed()))

        onView(withId(R.id.scheduleButton)).perform(click())
        allowingNotificationPermission {
            onView(withText(R.string.action_schedule)).inRoot(isDialog()).perform(click())
        }
        onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())

        // The Cancel menu follows WorkManager's state, which updates in the background.
        waitFor { passes { onView(withId(R.id.action_cancel)).check(matches(isEnabled())) } }
    }

    @Test
    fun scheduledNotification_appearsInTheShade_andTappingItOpensTheApp() {
        openDetailAndSchedule()
        device.pressHome()

        device.openNotification()
        val notification = device.wait(Until.findObject(By.text(item.title)), 30_000)
        assertNotNull("Notification did not appear in the shade", notification)

        notification.click()
        assertTrue(
            "Tapping the notification did not open the app",
            device.wait(Until.hasObject(By.pkg(appContext.packageName).depth(0)), 10_000),
        )
    }

    @Test
    fun afterItFires_theCancelMenuTurnsOffAgain() {
        openDetailAndSchedule()

        waitFor(timeoutMs = 30_000) {
            passes { onView(withId(R.id.action_cancel)).check(matches(not(isEnabled()))) }
        }
    }

    @Test
    fun cancelledNotification_neverAppears() {
        openDetailAndSchedule()

        onView(withId(R.id.action_cancel)).perform(click())

        // The cancel reached WorkManager.
        val workManager = WorkManager.getInstance(appContext)
        waitFor {
            workManager.getWorkInfosForUniqueWork(WorkManagerNotificationScheduler.workName(item.id)).get()
                .all { it.state == WorkInfo.State.CANCELLED }
        }
        waitFor { passes { onView(withId(R.id.action_cancel)).check(matches(not(isEnabled()))) } }

        Thread.sleep((item.delaySeconds + 5) * 1_000L) // past the moment it would have fired

        // Leave the app first: the detail screen shows the same title, and UI Automator also
        // searches the app window behind the shade.
        device.pressHome()
        device.openNotification()
        assertFalse(
            "A cancelled notification was shown",
            device.wait(Until.hasObject(By.text(item.title)), 3_000),
        )
    }
}
