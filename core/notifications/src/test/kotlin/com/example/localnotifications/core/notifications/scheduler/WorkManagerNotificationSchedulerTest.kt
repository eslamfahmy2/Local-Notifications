package com.example.localnotifications.core.notifications.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.localnotifications.core.domain.model.NotificationItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerNotificationSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerNotificationScheduler

    private fun item(id: Int, delay: Int = 10) = NotificationItem(id, "Title $id", delay)

    private fun infos(id: Int): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WorkManagerNotificationScheduler.workName(id)).get()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerNotificationScheduler(workManager)
    }

    @Test
    fun `schedule enqueues work that waits for the item delay`() {
        scheduler.schedule(item(1, delay = 40))

        val info = infos(1).single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(40_000L, info.initialDelayMillis)
    }

    @Test
    fun `scheduling again replaces the pending work`() {
        scheduler.schedule(item(1, delay = 10))
        scheduler.schedule(item(1, delay = 20))

        val pending = infos(1).filter { !it.state.isFinished }
        assertEquals(1, pending.size)
        assertEquals(20_000L, pending.single().initialDelayMillis)
    }

    @Test
    fun `cancel only affects the given notification`() {
        scheduler.schedule(item(1))
        scheduler.schedule(item(2))

        scheduler.cancel(1)

        assertTrue(infos(1).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(WorkInfo.State.ENQUEUED, infos(2).single().state)
    }

    @Test
    fun `cancelAll cancels every scheduled notification`() {
        scheduler.schedule(item(1))
        scheduler.schedule(item(2))

        scheduler.cancelAll()

        assertTrue(infos(1).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(infos(2).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `observeIsScheduled is true while pending`() = runTest {
        scheduler.schedule(item(1))

        assertTrue(scheduler.observeIsScheduled(1).first())
    }

    @Test
    fun `observeIsScheduled is false for unknown or cancelled notifications`() = runTest {
        assertFalse(scheduler.observeIsScheduled(99).first())

        scheduler.schedule(item(1))
        scheduler.cancel(1)

        assertFalse(scheduler.observeIsScheduled(1).first())
    }
}
