package com.example.localnotifications.core.domain.usecase

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SchedulerUseCasesTest {

    private val scheduler = mockk<NotificationScheduler>(relaxed = true)

    @Test
    fun `schedule delegates to scheduler`() {
        val item = NotificationItem(1, "A", 10)
        ScheduleNotificationUseCase(scheduler)(item)
        verify(exactly = 1) { scheduler.schedule(item) }
    }

    @Test
    fun `cancel delegates with id`() {
        CancelNotificationUseCase(scheduler)(3)
        verify(exactly = 1) { scheduler.cancel(3) }
    }

    @Test
    fun `cancel all delegates to scheduler`() {
        CancelAllNotificationsUseCase(scheduler)()
        verify(exactly = 1) { scheduler.cancelAll() }
    }
}
