package com.example.localnotifications.feature.list

import app.cash.turbine.test
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.domain.usecase.CancelAllNotificationsUseCase
import com.example.localnotifications.core.domain.usecase.GetNotificationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = mockk<NotificationRepository>()
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val items = listOf(NotificationItem(1, "A", 10), NotificationItem(2, "B", 20))

    private fun viewModel() = ListViewModel(
        getNotifications = GetNotificationsUseCase(repository),
        cancelAllNotifications = CancelAllNotificationsUseCase(scheduler),
    )

    @Test
    fun `Load success shows items`() = runTest {
        coEvery { repository.getNotifications() } returns Result.success(items)
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)

        assertEquals(ListState(isLoading = false, items = items, error = null), vm.state.value)
    }

    @Test
    fun `Load shows loading while in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.getNotifications() } coAnswers {
            gate.await()
            Result.success(items)
        }
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)
        assertTrue(vm.state.value.isLoading)

        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertEquals(items, vm.state.value.items)
    }

    @Test
    fun `Load failure maps domain errors`() = runTest {
        val cases = mapOf(
            DomainError.Network() to ListError.Network,
            DomainError.Parsing() to ListError.Parsing,
            DomainError.Unknown() to ListError.Unknown,
            DomainError.NotFound(1) to ListError.Unknown,
        )
        cases.forEach { (domainError, expected) ->
            coEvery { repository.getNotifications() } returns Result.failure(domainError)
            val vm = viewModel()

            vm.onIntent(ListIntent.Load)

            assertEquals(expected, vm.state.value.error)
            assertTrue(vm.state.value.items.isEmpty())
            assertFalse(vm.state.value.isLoading)
        }
    }

    @Test
    fun `Load is ignored when items are already present`() = runTest {
        coEvery { repository.getNotifications() } returns Result.success(items)
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)
        vm.onIntent(ListIntent.Load)

        coVerify(exactly = 1) { repository.getNotifications() }
    }

    @Test
    fun `Reload refreshes even when items are present`() = runTest {
        coEvery { repository.getNotifications() } returns Result.success(items)
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)
        vm.onIntent(ListIntent.Reload)

        coVerify(exactly = 2) { repository.getNotifications() }
    }

    @Test
    fun `Reload failure keeps previous items and sets error`() = runTest {
        coEvery { repository.getNotifications() } returns Result.success(items)
        val vm = viewModel()
        vm.onIntent(ListIntent.Load)

        coEvery { repository.getNotifications() } returns Result.failure(DomainError.Network())
        vm.onIntent(ListIntent.Reload)

        assertEquals(items, vm.state.value.items)
        assertEquals(ListError.Network, vm.state.value.error)
    }

    @Test
    fun `successful reload clears a previous error`() = runTest {
        coEvery { repository.getNotifications() } returns Result.failure(DomainError.Network())
        val vm = viewModel()
        vm.onIntent(ListIntent.Load)

        coEvery { repository.getNotifications() } returns Result.success(items)
        vm.onIntent(ListIntent.Reload)

        assertNull(vm.state.value.error)
        assertEquals(items, vm.state.value.items)
    }

    @Test
    fun `Reload is ignored while a load is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.getNotifications() } coAnswers {
            gate.await()
            Result.success(items)
        }
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)
        vm.onIntent(ListIntent.Reload)
        gate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { repository.getNotifications() }
    }

    @Test
    fun `empty feed shows no items and no error`() = runTest {
        coEvery { repository.getNotifications() } returns Result.success(emptyList())
        val vm = viewModel()

        vm.onIntent(ListIntent.Load)

        assertEquals(ListState(isLoading = false, items = emptyList(), error = null), vm.state.value)
    }

    @Test
    fun `CancelAllClicked works while a load is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.getNotifications() } coAnswers {
            gate.await()
            Result.success(items)
        }
        val vm = viewModel()
        vm.onIntent(ListIntent.Load)

        vm.effects.test {
            vm.onIntent(ListIntent.CancelAllClicked)
            assertEquals(ListEffect.AllCancelled, awaitItem())
        }
        verify(exactly = 1) { scheduler.cancelAll() }

        gate.complete(Unit)
        runCurrent()
        assertEquals(items, vm.state.value.items)
    }

    @Test
    fun `ItemClicked emits navigation effect`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(ListIntent.ItemClicked(2))
            assertEquals(ListEffect.NavigateToDetail(2), awaitItem())
        }
    }

    @Test
    fun `CancelAllClicked cancels everything and emits effect`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(ListIntent.CancelAllClicked)
            assertEquals(ListEffect.AllCancelled, awaitItem())
        }
        verify(exactly = 1) { scheduler.cancelAll() }
    }
}
