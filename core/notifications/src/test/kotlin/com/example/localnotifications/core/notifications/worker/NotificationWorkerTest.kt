package com.example.localnotifications.core.notifications.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemManager = context.getSystemService(NotificationManager::class.java)

    private fun worker(vararg input: Pair<String, Any?>) =
        TestListenableWorkerBuilder<NotificationWorker>(context)
            .setInputData(workDataOf(*input))
            .build()

    @Test
    fun `valid input posts a notification and succeeds`() {
        val result = worker(
            NotificationWorker.KEY_ID to 3,
            NotificationWorker.KEY_TITLE to "Hello",
        ).doWork()

        assertEquals(Result.success(), result)
        assertEquals(1, shadowOf(systemManager).allNotifications.size)
    }

    @Test
    fun `missing title fails without posting`() {
        val result = worker(NotificationWorker.KEY_ID to 3).doWork()

        assertEquals(Result.failure(), result)
        assertTrue(shadowOf(systemManager).allNotifications.isEmpty())
    }

    @Test
    fun `missing id fails without posting`() {
        val result = worker(NotificationWorker.KEY_TITLE to "Hello").doWork()

        assertEquals(Result.failure(), result)
        assertTrue(shadowOf(systemManager).allNotifications.isEmpty())
    }

    @Test
    fun `disabled notifications still succeed so work is not retried`() {
        shadowOf(systemManager).setNotificationsEnabled(false)

        val result = worker(
            NotificationWorker.KEY_ID to 3,
            NotificationWorker.KEY_TITLE to "Hello",
        ).doWork()

        assertEquals(Result.success(), result)
        assertTrue(shadowOf(systemManager).allNotifications.isEmpty())
    }
}
