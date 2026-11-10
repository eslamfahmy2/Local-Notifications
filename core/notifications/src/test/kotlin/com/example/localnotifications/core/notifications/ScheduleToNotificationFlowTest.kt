package com.example.localnotifications.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.notifications.scheduler.WorkManagerNotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End to end inside the module: real scheduler, real WorkManager (test mode), real worker,
 * real NotificationHelper. Only the passing of time is simulated, through [TestDriver].
 */
@RunWith(RobolectricTestRunner::class)
class ScheduleToNotificationFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemManager = context.getSystemService(NotificationManager::class.java)
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver
    private lateinit var scheduler: WorkManagerNotificationScheduler

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        scheduler = WorkManagerNotificationScheduler(workManager)
    }

    private fun pendingWork(id: Int): WorkInfo =
        workManager.getWorkInfosForUniqueWork(WorkManagerNotificationScheduler.workName(id)).get()
            .single { !it.state.isFinished }

    private fun latestWork(id: Int): WorkInfo =
        workManager.getWorkInfosForUniqueWork(WorkManagerNotificationScheduler.workName(id)).get().last()

    /** Simulates the delay of the pending notification [id] running out. */
    private fun fire(id: Int) = testDriver.setInitialDelayMet(pendingWork(id).id)

    private fun posted(): List<Notification> = shadowOf(systemManager).allNotifications

    private fun Notification.title(): String? = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test
    fun `nothing is posted before the delay runs out`() {
        scheduler.schedule(NotificationItem(1, "Hello", 10))

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `scheduled notification is posted with its title and id once the delay runs out`() = runTest {
        scheduler.schedule(NotificationItem(1, "Hello", 10))
        assertTrue(scheduler.observeIsScheduled(1).first())

        fire(1)

        val notification = shadowOf(systemManager).getNotification(1)
        assertNotNull(notification)
        assertEquals("Hello", notification.title())
        assertEquals(WorkInfo.State.SUCCEEDED, latestWork(1).state)
        assertFalse(scheduler.observeIsScheduled(1).first())
    }

    @Test
    fun `cancelled notification is never posted while others still fire`() {
        scheduler.schedule(NotificationItem(1, "One", 10))
        scheduler.schedule(NotificationItem(2, "Two", 20))

        scheduler.cancel(1)
        fire(2)

        assertEquals(listOf("Two"), posted().map { it.title() })
        assertEquals(WorkInfo.State.CANCELLED, latestWork(1).state)
    }

    @Test
    fun `cancelAll before the delay posts nothing`() {
        scheduler.schedule(NotificationItem(1, "One", 10))
        scheduler.schedule(NotificationItem(2, "Two", 20))

        scheduler.cancelAll()

        assertTrue(posted().isEmpty())
        assertEquals(WorkInfo.State.CANCELLED, latestWork(1).state)
        assertEquals(WorkInfo.State.CANCELLED, latestWork(2).state)
    }

    @Test
    fun `scheduling the same item twice fires only once with the latest data`() {
        scheduler.schedule(NotificationItem(1, "Old", 10))
        scheduler.schedule(NotificationItem(1, "New", 20))

        fire(1)

        assertEquals(listOf("New"), posted().map { it.title() })
    }

    @Test
    fun `with notifications disabled the work still succeeds and nothing is posted`() {
        shadowOf(systemManager).setNotificationsEnabled(false)
        scheduler.schedule(NotificationItem(1, "Hello", 10))

        fire(1)

        assertTrue(posted().isEmpty())
        assertEquals(WorkInfo.State.SUCCEEDED, latestWork(1).state)
    }

    @Test
    fun `tapping the notification opens the app launcher activity`() {
        val launcher = ComponentName(context.packageName, "com.example.FakeLauncherActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
        scheduler.schedule(NotificationItem(1, "Hello", 10))

        fire(1)

        val notification = shadowOf(systemManager).getNotification(1)
        assertTrue((notification.flags and Notification.FLAG_AUTO_CANCEL) != 0)
        val tapIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(launcher, tapIntent.component)
    }

    @Test
    fun `without a launcher activity the notification is still posted, just not tappable`() {
        scheduler.schedule(NotificationItem(1, "Hello", 10))

        fire(1)

        val notification = shadowOf(systemManager).getNotification(1)
        assertNotNull(notification)
        assertNull(notification.contentIntent)
    }
}
