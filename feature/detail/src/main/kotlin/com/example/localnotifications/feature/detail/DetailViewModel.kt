package com.example.localnotifications.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.usecase.CancelNotificationUseCase
import com.example.localnotifications.core.domain.usecase.GetNotificationUseCase
import com.example.localnotifications.core.domain.usecase.ObserveIsScheduledUseCase
import com.example.localnotifications.core.domain.usecase.ScheduleNotificationUseCase
import com.example.localnotifications.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The notification id arrives through [SavedStateHandle], which an Activity-scoped ViewModel
 * fills automatically from the launching Intent's extras (see [DetailArgs]).
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getNotification: GetNotificationUseCase,
    private val scheduleNotification: ScheduleNotificationUseCase,
    private val cancelNotification: CancelNotificationUseCase,
    observeIsScheduled: ObserveIsScheduledUseCase,
) : MviViewModel<DetailState, DetailIntent, DetailEffect>(DetailState()) {

    private val notificationId: Int? = savedStateHandle[DetailArgs.NOTIFICATION_ID]
    private var loadJob: Job? = null

    init {
        val id = notificationId
        if (id == null) {
            reduce { copy(isLoading = false, error = DetailError.NotFound) }
        } else {
            observeIsScheduled(id)
                .onEach { scheduled -> reduce { copy(isScheduled = scheduled) } }
                .launchIn(viewModelScope)
            load(id)
        }
    }

    override fun onIntent(intent: DetailIntent) {
        when (intent) {
            DetailIntent.Retry -> notificationId?.let(::load)
            DetailIntent.ScheduleClicked -> confirmSchedule()
            DetailIntent.ScheduleConfirmed -> schedule()
            DetailIntent.CancelClicked -> cancel()
        }
    }

    private fun load(id: Int) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            reduce { copy(isLoading = true, error = null) }
            getNotification(id).fold(
                onSuccess = { item -> reduce { copy(isLoading = false, notification = item) } },
                onFailure = { error -> reduce { copy(isLoading = false, error = error.toDetailError()) } },
            )
        }
    }

    private fun confirmSchedule() {
        val item = state.value.notification ?: return
        emit(DetailEffect.ShowConfirmScheduleDialog(item.delaySeconds))
    }

    private fun schedule() {
        val item = state.value.notification ?: return
        scheduleNotification(item)
        emit(DetailEffect.ShowScheduledDialog(item.delaySeconds))
    }

    private fun cancel() {
        val id = notificationId ?: return
        if (!state.value.isScheduled) return
        cancelNotification(id)
        emit(DetailEffect.ShowCancelled)
    }

    private fun Throwable.toDetailError(): DetailError = when (this) {
        is DomainError.NotFound -> DetailError.NotFound
        is DomainError.Network -> DetailError.Network
        is DomainError.Parsing -> DetailError.Parsing
        else -> DetailError.Unknown
    }
}
