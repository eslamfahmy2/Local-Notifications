package com.example.localnotifications.core.notifications.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.localnotifications.core.notifications.notification.NotificationHelper

/**
 * Runs when a scheduled delay elapses and posts the notification.
 * It has no injected dependencies, so WorkManager's default factory can create it.
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getInt(KEY_ID, INVALID_ID)
        val title = inputData.getString(KEY_TITLE)
        if (id == INVALID_ID || title.isNullOrBlank()) return Result.failure()

        // A denied permission is a user choice, not a failure worth retrying.
        NotificationHelper(applicationContext).show(id, title)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "notification_id"
        const val KEY_TITLE = "notification_title"
        private const val INVALID_ID = Int.MIN_VALUE
    }
}
