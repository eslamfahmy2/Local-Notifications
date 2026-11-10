package com.example.localnotifications.feature.list

import androidx.lifecycle.viewModelScope
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.usecase.CancelAllNotificationsUseCase
import com.example.localnotifications.core.domain.usecase.GetNotificationsUseCase
import com.example.localnotifications.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class ListViewModel @Inject constructor(
    private val getNotifications: GetNotificationsUseCase,
    private val cancelAllNotifications: CancelAllNotificationsUseCase,
) : MviViewModel<ListState, ListIntent, ListEffect>(ListState()) {

    private var loadJob: Job? = null

    override fun onIntent(intent: ListIntent) {
        when (intent) {
            ListIntent.Load -> if (state.value.items.isEmpty()) load()
            ListIntent.Reload -> load()
            is ListIntent.ItemClicked -> emit(ListEffect.NavigateToDetail(intent.id))
            ListIntent.CancelAllClicked -> {
                cancelAllNotifications()
                emit(ListEffect.AllCancelled)
            }
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            reduce { copy(isLoading = true, error = null) }
            getNotifications().fold(
                onSuccess = { items -> reduce { copy(isLoading = false, items = items) } },
                onFailure = { error -> reduce { copy(isLoading = false, error = error.toListError()) } },
            )
        }
    }

    private fun Throwable.toListError(): ListError = when (this) {
        is DomainError.Network -> ListError.Network
        is DomainError.Parsing -> ListError.Parsing
        else -> ListError.Unknown
    }
}
