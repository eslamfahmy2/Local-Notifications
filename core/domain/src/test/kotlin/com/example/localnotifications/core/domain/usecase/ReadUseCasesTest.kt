package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadUseCasesTest {

    private val repository = mockk<NotificationRepository>()
    private val scheduler = mockk<NotificationScheduler>()

    @Test
    fun `GetNotificationUseCase returns the item for the id`() = runTest {
        val item = NotificationItem(4, "D", 40)
        coEvery { repository.getNotification(4) } returns Result.success(item)

        assertEquals(item, GetNotificationUseCase(repository)(4).getOrNull())
    }

    @Test
    fun `GetNotificationUseCase propagates repository failure`() = runTest {
        coEvery { repository.getNotification(9) } returns Result.failure(DomainError.NotFound(9))

        val error = GetNotificationUseCase(repository)(9).exceptionOrNull()

        assertTrue(error is DomainError.NotFound)
        assertEquals(9, (error as DomainError.NotFound).id)
    }

    @Test
    fun `ObserveIsScheduledUseCase forwards the scheduler flow for the id`() = runTest {
        every { scheduler.observeIsScheduled(2) } returns flowOf(false, true, false)

        assertEquals(listOf(false, true, false), ObserveIsScheduledUseCase(scheduler)(2).toList())
    }
}
