package com.example.localnotifications.core.mvi

/** Immutable snapshot of everything a screen needs to render. */
interface UiState

/** Everything the user (or the lifecycle) can ask a screen to do. */
interface UiIntent

/** One-shot events (dialogs, navigation, messages) that must not be replayed on rotation. */
interface UiEffect
