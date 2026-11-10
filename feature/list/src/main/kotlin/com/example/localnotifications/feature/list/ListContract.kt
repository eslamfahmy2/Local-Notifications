package com.example.localnotifications.feature.list

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.mvi.UiEffect
import com.example.localnotifications.core.mvi.UiIntent
import com.example.localnotifications.core.mvi.UiState

/** Why loading failed. The UI maps these to localized text. */
enum class ListError { Network, Parsing, Unknown }

data class ListState(
    val isLoading: Boolean = false,
    val items: List<NotificationItem> = emptyList(),
    /** Set after a failed load. Previously loaded [items] are kept so the list does not vanish. */
    val error: ListError? = null,
) : UiState

sealed interface ListIntent : UiIntent {
    /** Load unless data is already present (first show, rotation, retry after nothing loaded). */
    data object Load : ListIntent

    /** Always reload: returning from the background, pull to refresh, retry. */
    data object Reload : ListIntent

    data class ItemClicked(val id: Int) : ListIntent
    data object CancelAllClicked : ListIntent
}

sealed interface ListEffect : UiEffect {
    data class NavigateToDetail(val id: Int) : ListEffect
    data object AllCancelled : ListEffect
}
