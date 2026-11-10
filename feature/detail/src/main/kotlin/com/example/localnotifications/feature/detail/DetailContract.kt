package com.example.localnotifications.feature.detail

import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.mvi.UiEffect
import com.example.localnotifications.core.mvi.UiIntent
import com.example.localnotifications.core.mvi.UiState

/** Name of the Activity extra that carries the notification id. */
object DetailArgs {
    const val NOTIFICATION_ID = "notification_id"
}

/** Why the notification could not be shown. The UI maps these to localized text. */
enum class DetailError { NotFound, Network, Parsing, Unknown }

data class DetailState(
    val isLoading: Boolean = true,
    val notification: NotificationItem? = null,
    /** True while a notification for this item is pending. Drives the Cancel action. */
    val isScheduled: Boolean = false,
    val error: DetailError? = null,
) : UiState

sealed interface DetailIntent : UiIntent {
    data object Retry : DetailIntent

    /** Asks the user to confirm; nothing is scheduled yet. */
    data object ScheduleClicked : DetailIntent

    /** The user confirmed: schedule now. */
    data object ScheduleConfirmed : DetailIntent
    data object CancelClicked : DetailIntent
}

sealed interface DetailEffect : UiEffect {
    /** Asks the user to confirm before scheduling; [delaySeconds] lets the UI say when it would fire. */
    data class ShowConfirmScheduleDialog(val delaySeconds: Int) : DetailEffect

    /** Confirms the notification was scheduled; [delaySeconds] lets the UI say when it will fire. */
    data class ShowScheduledDialog(val delaySeconds: Int) : DetailEffect
    data object ShowCancelled : DetailEffect
}
