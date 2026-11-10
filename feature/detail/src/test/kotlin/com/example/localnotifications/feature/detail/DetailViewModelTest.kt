package com.example.localnotifications.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.domain.usecase.CancelNotificationUseCase
import com.example.localnotifications.core.domain.usecase.GetNotificationUseCase
import com.example.localnotifications.core.domain.usecase.ObserveIsScheduledUseCase
import com.example.localnotifications.core.domain.usecase.ScheduleNotificationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = mockk<NotificationRepository>()
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val scheduled = MutableStateFlow(false)
    private val item = NotificationItem(id = 1, title = "Schedule me after 40 seconds.", delaySeconds = 40)

    private fun viewModel(id: Int? = 1) = DetailViewModel(
        savedStateHandle = SavedStateHandle(
            if (id == null) emptyMap() else mapOf(DetailArgs.NOTIFICATION_ID to id),
        ),
        getNotification = GetNotificationUseCase(repository),
        scheduleNotification = ScheduleNotificationUseCase(scheduler),
        cancelNotification = CancelNotificationUseCase(scheduler),
        observeIsScheduled = ObserveIsScheduledUseCase(scheduler),
    )

    private fun givenItemLoads() {
        coEvery { repository.getNotification(1) } returns Result.success(item)
        every { scheduler.observeIsScheduled(1) } returns scheduled
    }

    @Test
    fun `loads the notification for the id in the arguments`() = runTest {
        givenItemLoads()

        val vm = viewModel()

        assertEquals(item, vm.state.value.notification)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `missing id results in NotFound`() = runTest {
        val vm = viewModel(id = null)

        assertEquals(DetailError.NotFound, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `load failure is mapped and Retry recovers`() = runTest {
        every { scheduler.observeIsScheduled(1) } returns scheduled
        coEvery { repository.getNotification(1) } returns Result.failure(DomainError.Network())
        val vm = viewModel()
        assertEquals(DetailError.Network, vm.state.value.error)

        coEvery { repository.getNotification(1) } returns Result.success(item)
        vm.onIntent(DetailIntent.Retry)

        assertNull(vm.state.value.error)
        assertEquals(item, vm.state.value.notification)
    }

    @Test
    fun `Retry while a load is in flight does not start another`() = runTest {
        every { scheduler.observeIsScheduled(1) } returns scheduled
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.getNotification(1) } coAnswers {
            gate.await()
            Result.success(item)
        }
        val vm = viewModel()

        vm.onIntent(DetailIntent.Retry)
        vm.onIntent(DetailIntent.Retry)
        gate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { repository.getNotification(1) }
        assertEquals(item, vm.state.value.notification)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `Retry with a missing id does nothing`() = runTest {
        val vm = viewModel(id = null)

        vm.onIntent(DetailIntent.Retry)

        coVerify(exactly = 0) { repository.getNotification(any()) }
        assertEquals(DetailError.NotFound, vm.state.value.error)
    }

    @Test
    fun `failure maps every domain error`() = runTest {
        every { scheduler.observeIsScheduled(1) } returns scheduled
        val cases = mapOf(
            DomainError.Network() to DetailError.Network,
            DomainError.Parsing() to DetailError.Parsing,
            DomainError.Unknown() to DetailError.Unknown,
            IllegalStateException() to DetailError.Unknown,
        )
        cases.forEach { (failure, expected) ->
            coEvery { repository.getNotification(1) } returns Result.failure(failure)

            val state = viewModel().state.value

            assertEquals(expected, state.error)
            assertNull(state.notification)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `unknown id from the repository maps to NotFound`() = runTest {
        every { scheduler.observeIsScheduled(1) } returns scheduled
        coEvery { repository.getNotification(1) } returns Result.failure(DomainError.NotFound(1))

        assertEquals(DetailError.NotFound, viewModel().state.value.error)
    }

    @Test
    fun `isScheduled mirrors the scheduler`() = runTest {
        givenItemLoads()
        val vm = viewModel()
        assertFalse(vm.state.value.isScheduled)

        scheduled.value = true
        assertTrue(vm.state.value.isScheduled)

        scheduled.value = false
        assertFalse(vm.state.value.isScheduled)
    }

    @Test
    fun `ScheduleClicked only asks for confirmation and schedules nothing`() = runTest {
        givenItemLoads()
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(DetailIntent.ScheduleClicked)
            assertEquals(DetailEffect.ShowConfirmScheduleDialog(40), awaitItem())
        }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `ScheduleConfirmed schedules the item and confirms with its delay`() = runTest {
        givenItemLoads()
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(DetailIntent.ScheduleConfirmed)
            assertEquals(DetailEffect.ShowScheduledDialog(40), awaitItem())
        }
        verify(exactly = 1) { scheduler.schedule(item) }
    }

    @Test
    fun `ScheduleClicked does nothing when the item failed to load`() = runTest {
        every { scheduler.observeIsScheduled(1) } returns scheduled
        coEvery { repository.getNotification(1) } returns Result.failure(DomainError.Network())
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(DetailIntent.ScheduleClicked)
            expectNoEvents()
        }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `CancelClicked cancels only this notification when scheduled`() = runTest {
        givenItemLoads()
        val vm = viewModel()
        scheduled.value = true

        vm.effects.test {
            vm.onIntent(DetailIntent.CancelClicked)
            assertEquals(DetailEffect.ShowCancelled, awaitItem())
        }
        verify(exactly = 1) { scheduler.cancel(1) }
    }

    @Test
    fun `CancelClicked does nothing when not scheduled`() = runTest {
        givenItemLoads()
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(DetailIntent.CancelClicked)
            expectNoEvents()
        }
        verify(exactly = 0) { scheduler.cancel(any()) }
    }
}
