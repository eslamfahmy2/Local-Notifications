package com.example.localnotifications.core.notifications.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.notifications.worker.NotificationWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [NotificationScheduler] backed by WorkManager.
 *
 * Each notification is one unique work item named after its id, so scheduling again replaces
 * the pending one and cancelling targets exactly one notification.
 * Timing is not exact: WorkManager may fire a few seconds late.
 */
class WorkManagerNotificationScheduler @Inject constructor(
    private val workManager: WorkManager,
) : NotificationScheduler {

    override fun schedule(item: NotificationItem) {
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(item.delaySeconds.toLong(), TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    NotificationWorker.KEY_ID to item.id,
                    NotificationWorker.KEY_TITLE to item.title,
                ),
            )
            .addTag(TAG_ALL)
            .build()

        workManager.enqueueUniqueWork(workName(item.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(id: Int) {
        workManager.cancelUniqueWork(workName(id))
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_ALL)
    }

    override fun observeIsScheduled(id: Int): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(id))
            .map { infos -> infos.any { !it.state.isFinished } }
            .distinctUntilChanged()

    companion object {
        const val TAG_ALL = "local_notification"
        fun workName(id: Int) = "local_notification_$id"
    }
}
