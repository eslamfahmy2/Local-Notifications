package com.example.localnotifications.ui.detail

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.R
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.di.DataModule
import com.example.localnotifications.di.NotificationsModule
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.core.testing.FakeNotificationScheduler
import com.example.localnotifications.testing.clickMenuItem
import com.example.localnotifications.testing.idleMain
import com.example.localnotifications.testing.isMenuItemEnabled
import com.example.localnotifications.testing.latestAlertDialog
import com.example.localnotifications.testing.read
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(DataModule::class, NotificationsModule::class)
@RunWith(AndroidJUnit4::class)
class DetailActivityTest {

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

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val item = NotificationItem(1, "Schedule me after 10 seconds.", 10)
    private var scenario: ActivityScenario<DetailActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.result = Result.success(listOf(item))
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(id: Int? = item.id): ActivityScenario<DetailActivity> {
        val intent = Intent(context, DetailActivity::class.java)
        if (id != null) intent.putExtra(DetailArgs.NOTIFICATION_ID, id)
        return ActivityScenario.launch<DetailActivity>(intent).also {
            scenario = it
            idleMain()
        }
    }

    private fun grantNotificationPermission() =
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun disableNotifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

    /** Taps Schedule and accepts the confirmation dialog. */
    private fun clickSchedule() {
        tapScheduleButton()
        latestAlertDialog()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMain()
    }

    private fun tapScheduleButton() {
        onView(withId(R.id.scheduleButton)).perform(click())
        idleMain()
    }

    private fun dialogMessage(): String =
        latestAlertDialog()!!.findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun scheduledMessage(): String =
        context.getString(R.string.scheduled_message, "10 seconds")

    @Test
    fun `shows the notification title and the schedule button`() {
        launch()

        onView(withText(item.title)).check(matches(isDisplayed()))
        onView(withId(R.id.scheduleButton)).check(matches(isDisplayed()))
        onView(withId(R.id.errorGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `Cancel is disabled while nothing is scheduled`() {
        val scenario = launch()

        assertFalse(scenario.read { it.isMenuItemEnabled(R.id.action_cancel) })
        assertFalse(scenario.read { it.clickMenuItem(R.id.action_cancel) })
    }

    @Test
    fun `Cancel is enabled while scheduled and cancels only this notification`() {
        scheduler.schedule(item)
        scheduler.schedule(NotificationItem(2, "Other", 20))
        val scenario = launch()

        assertTrue(scenario.read { it.isMenuItemEnabled(R.id.action_cancel) })
        assertTrue(scenario.read { it.clickMenuItem(R.id.action_cancel) })
        idleMain()

        assertEquals(setOf(2), scheduler.pending.keys)
        onView(withText(R.string.notification_cancelled)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        assertFalse(scenario.read { it.isMenuItemEnabled(R.id.action_cancel) })
    }

    @Test
    fun `Schedule asks for confirmation first and schedules nothing yet`() {
        val scenario = launch()

        tapScheduleButton()

        assertTrue(latestAlertDialog()!!.isShowing)
        assertEquals(context.getString(R.string.confirm_schedule_message, "10 seconds"), dialogMessage())
        assertTrue(scheduler.pending.isEmpty())
        assertNull(scenario.read { shadowOf(it).lastRequestedPermission })
    }

    @Test
    fun `cancelling the confirmation schedules nothing`() {
        grantNotificationPermission()
        launch()

        tapScheduleButton()
        latestAlertDialog()!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idleMain()

        assertTrue(scheduler.pending.isEmpty())
        assertFalse(latestAlertDialog()!!.isShowing)
    }

    @Test
    fun `with permission granted, Schedule schedules and confirms with the delay`() {
        grantNotificationPermission()
        val scenario = launch()

        clickSchedule()

        assertEquals(item, scheduler.pending[item.id])
        assertNull(scenario.read { shadowOf(it).lastRequestedPermission })
        val dialog = latestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog!!.isShowing)
        assertEquals(scheduledMessage(), dialogMessage())
        assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
        assertTrue(scenario.read { it.isMenuItemEnabled(R.id.action_cancel) })
    }

    @Test
    fun `on Android 13+ without permission, Schedule asks for it first`() {
        val scenario = launch()

        clickSchedule()

        val request = scenario.read { shadowOf(it).lastRequestedPermission }
        assertNotNull(request)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in request.requestedPermissions)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun `if the permission is denied it still schedules and warns with a Settings button`() {
        val scenario = launch()
        clickSchedule()
        val request = scenario.read { shadowOf(it).lastRequestedPermission }
        disableNotifications()

        scenario.onActivity {
            @Suppress("DEPRECATION")
            it.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
        idleMain()

        assertEquals(item, scheduler.pending[item.id])
        assertTrue(dialogMessage().contains(context.getString(R.string.notifications_disabled_warning)))
        assertEquals(View.VISIBLE, latestAlertDialog()!!.getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
    }

    @Test
    fun `the Settings button opens this app's notification settings`() {
        grantNotificationPermission()
        disableNotifications()
        launch()
        clickSchedule()

        latestAlertDialog()!!.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        idleMain()

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
        assertEquals(context.packageName, started.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13, Schedule needs no permission request`() {
        val scenario = launch()

        clickSchedule()

        assertNull(scenario.read { shadowOf(it).lastRequestedPermission })
        assertEquals(item, scheduler.pending[item.id])
        assertEquals(scheduledMessage(), dialogMessage())
    }

    @Test
    fun `unknown id shows not found with Retry`() {
        launch(id = 99)

        onView(withText(R.string.error_not_found)).check(matches(isDisplayed()))
        onView(withId(R.id.retryButton)).check(matches(isDisplayed()))
        onView(withId(R.id.contentGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `missing id shows not found`() {
        launch(id = null)

        onView(withText(R.string.error_not_found)).check(matches(isDisplayed()))
    }

    @Test
    fun `rotation keeps the loaded notification`() {
        val scenario = launch()

        scenario.recreate()
        idleMain()

        onView(withText(item.title)).check(matches(isDisplayed()))
    }
}
