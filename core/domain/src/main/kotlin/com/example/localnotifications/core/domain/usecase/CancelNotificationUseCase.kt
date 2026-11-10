package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import javax.inject.Inject

class CancelNotificationUseCase @Inject constructor(
    private val scheduler: NotificationScheduler,
) {
    operator fun invoke(id: Int) = scheduler.cancel(id)
}
