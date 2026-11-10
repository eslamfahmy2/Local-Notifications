package com.example.localnotifications.ui.list

import com.example.localnotifications.core.domain.model.NotificationItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDiffCallbackTest {

    private val item = NotificationItem(1, "A", 10)

    @Test
    fun `items with the same id are the same item`() {
        assertTrue(NotificationDiffCallback.areItemsTheSame(item, item.copy(title = "B")))
        assertFalse(NotificationDiffCallback.areItemsTheSame(item, item.copy(id = 2)))
    }

    @Test
    fun `contents differ when any field differs`() {
        assertTrue(NotificationDiffCallback.areContentsTheSame(item, item.copy()))
        assertFalse(NotificationDiffCallback.areContentsTheSame(item, item.copy(delaySeconds = 20)))
    }
}
