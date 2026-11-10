package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import javax.inject.Inject

class ScheduleNotificationUseCase @Inject constructor(
    private val scheduler: NotificationScheduler,
) {
    operator fun invoke(item: NotificationItem) = scheduler.schedule(item)
}
