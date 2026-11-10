package com.example.localnotifications.core.data.remote.dto

/**
 * Raw feed item. Every field is nullable because the feed is untrusted input;
 * validation happens in the mapper, not in the parser.
 */
data class NotificationDto(
    val id: Int?,
    val title: String?,
    val timeInSeconds: Int?,
)
