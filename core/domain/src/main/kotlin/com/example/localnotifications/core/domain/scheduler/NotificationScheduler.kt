package com.example.localnotifications.core.domain.scheduler

import com.example.localnotifications.core.domain.model.NotificationItem
import kotlinx.coroutines.flow.Flow

/** Schedules local notifications. The mechanism (WorkManager, AlarmManager...) is hidden. */
interface NotificationScheduler {

    /** Schedules [item] to fire after its delay. Re-scheduling replaces the pending one. */
    fun schedule(item: NotificationItem)

    /** Cancels the pending notification with [id], if any. */
    fun cancel(id: Int)

    /** Cancels every pending notification. */
    fun cancelAll()

    /** Emits whether a notification with [id] is currently pending. */
    fun observeIsScheduled(id: Int): Flow<Boolean>
}
