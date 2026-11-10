package com.example.localnotifications.core.testing

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory scheduler: nothing ever fires, it only tracks what is pending. */
class FakeNotificationScheduler : NotificationScheduler {

    private val scheduled = MutableStateFlow<Map<Int, NotificationItem>>(emptyMap())

    val pending: Map<Int, NotificationItem> get() = scheduled.value

    override fun schedule(item: NotificationItem) = scheduled.update { it + (item.id to item) }

    override fun cancel(id: Int) = scheduled.update { it - id }

    override fun cancelAll() = scheduled.update { emptyMap() }

    override fun observeIsScheduled(id: Int): Flow<Boolean> =
        scheduled.map { id in it }.distinctUntilChanged()
}
