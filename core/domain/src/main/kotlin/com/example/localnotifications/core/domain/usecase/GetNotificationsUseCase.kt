package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import javax.inject.Inject

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(): Result<List<NotificationItem>> = repository.getNotifications()
}
