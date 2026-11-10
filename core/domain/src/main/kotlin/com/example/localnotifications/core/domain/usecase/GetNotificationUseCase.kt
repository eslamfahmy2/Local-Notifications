package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import javax.inject.Inject

class GetNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(id: Int): Result<NotificationItem> = repository.getNotification(id)
}
