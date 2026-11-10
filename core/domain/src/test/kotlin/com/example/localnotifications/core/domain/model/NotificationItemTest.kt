package com.example.localnotifications.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationItemTest {

    @Test
    fun `valid item keeps its values`() {
        val item = NotificationItem(id = 1, title = "Hello", delaySeconds = 10)
        assertEquals(10, item.delaySeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank title is rejected`() {
        NotificationItem(id = 1, title = " ", delaySeconds = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive delay is rejected`() {
        NotificationItem(id = 1, title = "Hello", delaySeconds = 0)
    }
}
