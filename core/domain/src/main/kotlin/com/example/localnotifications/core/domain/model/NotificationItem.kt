package com.example.localnotifications.core.domain.model

/**
 * A notification definition coming from the feed.
 *
 * @property id stable identifier, also used to key scheduled work.
 * @property title text shown in the list, detail screen and the notification itself.
 * @property delaySeconds how long after scheduling the notification should fire.
 */
data class NotificationItem(
    val id: Int,
    val title: String,
    val delaySeconds: Int,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(delaySeconds > 0) { "delaySeconds must be positive, was $delaySeconds" }
    }
}
