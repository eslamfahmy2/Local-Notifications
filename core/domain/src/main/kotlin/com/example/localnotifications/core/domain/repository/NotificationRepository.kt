package com.example.localnotifications.core.domain.repository

import com.example.localnotifications.core.domain.model.NotificationItem

/** Source of notification definitions. Implementations decide where they come from. */
interface NotificationRepository {

    /** Loads the full list, always refreshing from the source. */
    suspend fun getNotifications(): Result<List<NotificationItem>>

    /** Returns a single item, using already loaded data when available. */
    suspend fun getNotification(id: Int): Result<NotificationItem>
}
