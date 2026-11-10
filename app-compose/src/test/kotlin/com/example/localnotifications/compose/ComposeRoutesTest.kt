package com.example.localnotifications.compose

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.compose.di.DataModule
import com.example.localnotifications.compose.di.NotificationsModule
import com.example.localnotifications.compose.ui.detail.DetailActivity
import com.example.localnotifications.compose.ui.list.MainActivity
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.core.testing.FakeNotificationScheduler
import com.example.localnotifications.feature.detail.DetailArgs
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Compose routes inside their real Activities. Covers what the stateless screen tests cannot:
 * the Compose-specific lifecycle reload rule, effects (navigation, snackbars, dialog) and
 * the permission flow. Data and scheduling are faked through Hilt.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, NotificationsModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ComposeRoutesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val repository = FakeNotificationRepository()
    private val scheduler = FakeNotificationScheduler()

    @BindValue
    @JvmField
    val boundRepository: NotificationRepository = repository

    @BindValue
    @JvmField
    val boundScheduler: NotificationScheduler = scheduler

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = listOf(NotificationItem(1, "First", 10), NotificationItem(2, "Second", 20))
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.result = Result.success(items)
    }

    @After
    fun tearDown() = scenarios.forEach { it.close() }

    private fun text(id: Int) = context.getString(id)

    private fun launchList(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenarios += it
            composeRule.waitForIdle()
        }

    private fun launchDetail(id: Int = 1): ActivityScenario<DetailActivity> {
        val intent = Intent(context, DetailActivity::class.java).putExtra(DetailArgs.NOTIFICATION_ID, id)
        return ActivityScenario.launch<DetailActivity>(intent).also {
            scenarios += it
            composeRule.waitForIdle()
        }
    }

    private fun grantNotificationPermission() =
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    /** Taps Schedule and accepts the confirmation dialog. */
    private fun clickSchedule() {
        tapScheduleButton()
        composeRule.onNodeWithText(text(R.string.action_schedule)).performClick()
        composeRule.waitForIdle()
    }

    private fun tapScheduleButton() {
        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).performClick()
        composeRule.waitForIdle()
    }

    // List route

    @Test
    fun list_rotationDoesNotReload() {
        val scenario = launchList()
        composeRule.onNodeWithText("First").assertExists()

        scenario.recreate()
        composeRule.waitForIdle()

        assertEquals(1, repository.listCalls)
        composeRule.onNodeWithText("First").assertExists()
    }

    @Test
    fun list_returningFromTheDetailScreenDoesNotReload() {
        val scenario = launchList()

        // Stopped while the app stays in the foreground, as when the detail screen covers it.
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun list_returningFromTheBackgroundReloads() {
        val scenario = launchList()
        val app = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

        scenario.moveToState(Lifecycle.State.CREATED)
        app.currentState = Lifecycle.State.STARTED
        app.currentState = Lifecycle.State.CREATED // app-wide ON_STOP, as after pressing Home
        scenario.moveToState(Lifecycle.State.RESUMED)
        app.currentState = Lifecycle.State.RESUMED
        composeRule.waitForIdle()

        assertEquals(2, repository.listCalls)
    }

    @Test
    fun list_tappingACardOpensDetailForThatId() {
        val scenario = launchList()

        composeRule.onNodeWithText("Second").performClick()
        composeRule.waitForIdle()

        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertNotNull(started)
        assertEquals(DetailActivity::class.java.name, started!!.component?.className)
        assertEquals(2, started!!.getIntExtra(DetailArgs.NOTIFICATION_ID, -1))
    }

    @Test
    fun list_cancelAllCancelsEverythingAndShowsSnackbar() {
        items.forEach(scheduler::schedule)
        launchList()

        composeRule.onNodeWithText(text(R.string.action_cancel_all)).performClick()
        composeRule.waitForIdle()

        assertTrue(scheduler.pending.isEmpty())
        composeRule.onNodeWithText(text(R.string.all_cancelled)).assertExists()
    }

    // Detail route

    @Test
    fun detail_scheduleAsksForConfirmationFirst_thatSurvivesRotation() {
        val scenario = launchDetail()

        tapScheduleButton()

        composeRule.onNodeWithText(text(R.string.confirm_schedule_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.confirm_schedule_message, "10 seconds")).assertExists()
        assertTrue(scheduler.pending.isEmpty())

        scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.confirm_schedule_title)).assertExists()
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun detail_cancellingTheConfirmationSchedulesNothing() {
        grantNotificationPermission()
        launchDetail()
        tapScheduleButton()

        // The top bar also has a "Cancel" button, so pick the one inside the dialog.
        composeRule.onNode(hasText(context.getString(android.R.string.cancel)) and hasAnyAncestor(isDialog()))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.confirm_schedule_title)).assertDoesNotExist()
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun detail_withPermission_schedulesAndShowsDialog_thatSurvivesRotation() {
        grantNotificationPermission()
        val scenario = launchDetail()

        clickSchedule()

        assertEquals(items[0], scheduler.pending[1])
        composeRule.onNodeWithText(text(R.string.scheduled_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.scheduled_message, "10 seconds")).assertExists()

        scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.scheduled_title)).assertExists()
    }

    @Test
    fun detail_okClosesTheDialog() {
        grantNotificationPermission()
        launchDetail()
        clickSchedule()

        composeRule.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.scheduled_title)).assertDoesNotExist()
    }

    @Test
    fun detail_withoutPermission_asksForItFirst() {
        val scenario = launchDetail()

        clickSchedule()

        var requested: Array<String>? = null
        scenario.onActivity { requested = shadowOf(it).lastRequestedPermission?.requestedPermissions }
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requested.orEmpty())
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun detail_permissionDenied_stillSchedules_warns_andSettingsOpensNotificationSettings() {
        val scenario = launchDetail()
        clickSchedule()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

        scenario.onActivity {
            val request = shadowOf(it).lastRequestedPermission
            @Suppress("DEPRECATION")
            it.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
        composeRule.waitForIdle()

        assertEquals(items[0], scheduler.pending[1])
        composeRule.onNode(hasText(text(R.string.notifications_disabled_warning), substring = true)).assertExists()

        composeRule.onNode(hasText(text(R.string.action_settings)) and hasClickAction()).performClick()
        composeRule.waitForIdle()

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
        assertEquals(context.packageName, started.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun detail_cancelWhileScheduled_cancelsOnlyThisOneAndShowsSnackbar() {
        items.forEach(scheduler::schedule)
        launchDetail(id = 1)

        composeRule.onNode(hasText(text(R.string.action_cancel)) and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(setOf(2), scheduler.pending.keys)
        composeRule.onNodeWithText(text(R.string.notification_cancelled)).assertExists()
    }

    @Test
    fun detail_unknownIdShowsNotFound() {
        launchDetail(id = 99)

        composeRule.onNodeWithText(text(R.string.error_not_found)).assertExists()
    }
}
