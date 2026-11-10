package com.example.localnotifications.compose

import android.content.Intent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.compose.di.DataModule
import com.example.localnotifications.compose.di.NotificationsModule
import com.example.localnotifications.compose.ui.detail.DetailActivity
import com.example.localnotifications.compose.ui.list.MainActivity
import com.example.localnotifications.core.devicetesting.allowingNotificationPermission
import com.example.localnotifications.core.devicetesting.appContext
import com.example.localnotifications.core.devicetesting.bringToFront
import com.example.localnotifications.core.devicetesting.device
import com.example.localnotifications.core.devicetesting.reopenApp
import com.example.localnotifications.core.devicetesting.waitFor
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.core.testing.FakeNotificationScheduler
import com.example.localnotifications.feature.detail.DetailArgs
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-specific behaviour that only a real device proves: physical rotation (the dialogs use
 * rememberSaveable), the system permission dialog
 * through rememberLauncherForActivityResult, and the real landscape layout.
 * Data and scheduling are faked; real notifications are covered by the Views device tests.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, NotificationsModule::class)
@RunWith(AndroidJUnit4::class)
class ComposeDeviceTest {

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

    private fun text(id: Int) = appContext.getString(id)

    /** True once a node with [value] is on screen; safe to call while an Activity is being recreated. */
    private fun shows(value: String): Boolean =
        composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()

    private fun rotateToLandscape() {
        device.setOrientationLeft()
        device.waitForIdle()
    }

    private fun launchDetail(): ActivityScenario<DetailActivity> {
        val intent = Intent(appContext, DetailActivity::class.java).putExtra(DetailArgs.NOTIFICATION_ID, 1)
        return ActivityScenario.launch<DetailActivity>(intent).also { scenario = it }
    }

    @Test
    fun list_rotatingTheDevice_doesNotReload() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(5_000) { shows("First") }

        rotateToLandscape()
        composeRule.waitUntil(5_000) { shows("Second") }
        device.setOrientationNatural()
        device.waitForIdle()
        composeRule.waitUntil(5_000) { shows("First") }

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun list_leavingThroughHomeAndReturning_reloads() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        composeRule.waitUntil(5_000) { shows("First") }
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
    }

    @Test
    fun list_openingDetailsAndGoingBack_doesNotReload() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        composeRule.waitUntil(5_000) { shows("First") }
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitUntil(5_000) { shows(text(R.string.schedule_this_notification)) }

        device.pressBack()

        waitFor { main.state == Lifecycle.State.RESUMED }
        composeRule.waitUntil(5_000) { shows("First") }
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun list_leavingThroughHomeOnDetails_reloadsWhenGoingBack() {
        val main = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        composeRule.waitUntil(5_000) { shows("First") }
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitUntil(5_000) { shows(text(R.string.schedule_this_notification)) }

        device.pressHome()
        // The app counts as backgrounded ~700 ms after its last screen stops.
        waitFor { ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.CREATED }
        reopenApp()
        composeRule.waitUntil(5_000) { shows(text(R.string.schedule_this_notification)) }
        assertEquals(1, repository.listCalls)

        device.pressBack()

        waitFor { main.state == Lifecycle.State.RESUMED }
        waitFor { repository.listCalls == 2 }
    }

    @Test
    fun detail_cancellingTheConfirmation_schedulesNothing() {
        launchDetail()
        composeRule.waitUntil(5_000) { shows("First") }

        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).performClick()
        composeRule.waitUntil(5_000) { shows(text(R.string.confirm_schedule_title)) }
        // The top bar also has a "Cancel" button, so pick the one inside the dialog.
        composeRule.onNode(hasText(text(android.R.string.cancel)) and hasAnyAncestor(isDialog())).performClick()

        composeRule.waitUntil(5_000) { !shows(text(R.string.confirm_schedule_title)) }
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun detail_realPermissionDialog_thenConfirmationThatSurvivesRotation() {
        launchDetail()
        composeRule.waitUntil(5_000) { shows("First") }

        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).performClick()
        composeRule.waitUntil(5_000) { shows(text(R.string.confirm_schedule_title)) }
        allowingNotificationPermission {
            composeRule.onNodeWithText(text(R.string.action_schedule)).performClick()
        }

        composeRule.waitUntil(5_000) { shows(text(R.string.scheduled_title)) }
        assertEquals(items[0], scheduler.pending[1])

        rotateToLandscape()
        composeRule.waitUntil(5_000) { shows(text(R.string.scheduled_title)) }

        composeRule.onNodeWithText(appContext.getString(android.R.string.ok)).performClick()
        composeRule.waitUntil(5_000) { !shows(text(R.string.scheduled_title)) }
    }

    @Test
    fun detail_inRealLandscape_putsTheButtonBesideTheTitle() {
        launchDetail()
        composeRule.waitUntil(5_000) { shows("First") }

        rotateToLandscape()

        composeRule.waitUntil(5_000) {
            runCatching {
                val title = composeRule.onNodeWithText("First").getUnclippedBoundsInRoot()
                val button = composeRule.onNodeWithText(text(R.string.schedule_this_notification))
                    .getUnclippedBoundsInRoot()
                button.left >= title.right && button.top < title.bottom && title.top < button.bottom
            }.getOrDefault(false)
        }
    }
}
