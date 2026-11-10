package com.example.localnotifications.core.data.mapper

import com.example.localnotifications.core.data.remote.dto.NotificationDto
import com.example.localnotifications.core.domain.model.NotificationItem

/**
 * Converts a raw item into a domain object, or null when it is unusable.
 * Invalid items are skipped so one bad entry cannot hide the rest of the feed.
 */
fun NotificationDto.toDomainOrNull(): NotificationItem? {
    val id = id ?: return null
    val title = title?.takeIf { it.isNotBlank() } ?: return null
    val delay = timeInSeconds?.takeIf { it > 0 } ?: return null
    return NotificationItem(id = id, title = title, delaySeconds = delay)
}
