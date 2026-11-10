package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveIsScheduledUseCase @Inject constructor(
    private val scheduler: NotificationScheduler,
) {
    operator fun invoke(id: Int): Flow<Boolean> = scheduler.observeIsScheduled(id)
}
