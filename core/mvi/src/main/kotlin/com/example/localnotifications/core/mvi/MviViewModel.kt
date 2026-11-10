package com.example.localnotifications.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base of the unidirectional loop:
 * UI sends an intent, the ViewModel reduces it into a new [state] and may emit [effects].
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    // Unlimited so an effect is never dropped, and a Channel so each effect is consumed once:
    // effects emitted while the UI is not collecting (e.g. during rotation) are delivered later.
    private val _effects = Channel<E>(Channel.UNLIMITED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)

    protected fun reduce(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    protected fun emit(effect: E) {
        _effects.trySend(effect)
    }
}
