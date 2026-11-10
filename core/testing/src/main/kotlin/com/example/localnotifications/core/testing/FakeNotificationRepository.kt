package com.example.localnotifications.core.testing

import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import kotlinx.coroutines.CompletableDeferred

/** Repository the UI tests control: what it returns, and when (via [gate]). */
class FakeNotificationRepository : NotificationRepository {

    var result: Result<List<NotificationItem>> = Result.success(emptyList())

    /** When set, loads suspend until it completes, so tests can see the loading state. */
    var gate: CompletableDeferred<Unit>? = null

    var listCalls = 0
        private set

    override suspend fun getNotifications(): Result<List<NotificationItem>> {
        listCalls++
        gate?.await()
        return result
    }

    override suspend fun getNotification(id: Int): Result<NotificationItem> {
        gate?.await()
        return result.mapCatching { items -> items.firstOrNull { it.id == id } ?: throw DomainError.NotFound(id) }
    }
}
