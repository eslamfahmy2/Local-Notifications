package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetNotificationsUseCaseTest {

    private val repository = mockk<NotificationRepository>()
    private val useCase = GetNotificationsUseCase(repository)

    @Test
    fun `returns items from repository`() = runTest {
        val items = listOf(NotificationItem(1, "A", 10), NotificationItem(2, "B", 20))
        coEvery { repository.getNotifications() } returns Result.success(items)

        assertEquals(items, useCase().getOrNull())
    }

    @Test
    fun `propagates repository failure`() = runTest {
        coEvery { repository.getNotifications() } returns Result.failure(DomainError.Network())

        assertTrue(useCase().exceptionOrNull() is DomainError.Network)
    }
}
