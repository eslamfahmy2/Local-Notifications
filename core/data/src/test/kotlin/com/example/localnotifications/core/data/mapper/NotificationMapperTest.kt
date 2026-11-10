package com.example.localnotifications.core.data.mapper

import com.example.localnotifications.core.data.remote.dto.NotificationDto
import com.example.localnotifications.core.domain.model.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationMapperTest {

    @Test
    fun `valid dto maps to domain item`() {
        assertEquals(
            NotificationItem(1, "Hello", 10),
            NotificationDto(1, "Hello", 10).toDomainOrNull(),
        )
    }

    @Test
    fun `missing id is rejected`() = assertNull(NotificationDto(null, "Hello", 10).toDomainOrNull())

    @Test
    fun `missing or blank title is rejected`() {
        assertNull(NotificationDto(1, null, 10).toDomainOrNull())
        assertNull(NotificationDto(1, "  ", 10).toDomainOrNull())
    }

    @Test
    fun `missing or non positive delay is rejected`() {
        assertNull(NotificationDto(1, "Hello", null).toDomainOrNull())
        assertNull(NotificationDto(1, "Hello", 0).toDomainOrNull())
        assertNull(NotificationDto(1, "Hello", -5).toDomainOrNull())
    }
}
