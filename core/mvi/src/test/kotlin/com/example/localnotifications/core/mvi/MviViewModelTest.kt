package com.example.localnotifications.core.mvi

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private data class CounterState(val count: Int = 0) : UiState

private sealed interface CounterIntent : UiIntent {
    data object Increment : CounterIntent
    data object Announce : CounterIntent
}

private data class Announced(val count: Int) : UiEffect

private class CounterViewModel : MviViewModel<CounterState, CounterIntent, Announced>(CounterState()) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> reduce { copy(count = count + 1) }
            CounterIntent.Announce -> emit(Announced(state.value.count))
        }
    }
}

class MviViewModelTest {

    private val viewModel = CounterViewModel()

    @Test
    fun `starts with the initial state`() {
        assertEquals(CounterState(0), viewModel.state.value)
    }

    @Test
    fun `reduce produces a new state from the previous one`() {
        viewModel.onIntent(CounterIntent.Increment)
        viewModel.onIntent(CounterIntent.Increment)

        assertEquals(CounterState(2), viewModel.state.value)
    }

    @Test
    fun `effects are delivered in order`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(CounterIntent.Announce)
            viewModel.onIntent(CounterIntent.Increment)
            viewModel.onIntent(CounterIntent.Announce)

            assertEquals(Announced(0), awaitItem())
            assertEquals(Announced(1), awaitItem())
        }
    }

    @Test
    fun `effects emitted before collection are not lost`() = runTest {
        viewModel.onIntent(CounterIntent.Announce)

        viewModel.effects.test {
            assertEquals(Announced(0), awaitItem())
        }
    }

    @Test
    fun `a burst of effects before collection is delivered in full and in order`() = runTest {
        repeat(500) {
            viewModel.onIntent(CounterIntent.Announce)
            viewModel.onIntent(CounterIntent.Increment)
        }

        viewModel.effects.test {
            repeat(500) { assertEquals(Announced(it), awaitItem()) }
            expectNoEvents()
        }
    }

    @Test
    fun `an effect is consumed only once`() = runTest {
        viewModel.onIntent(CounterIntent.Announce)
        viewModel.effects.test { assertEquals(Announced(0), awaitItem()) }

        viewModel.effects.test { expectNoEvents() }
    }
}
