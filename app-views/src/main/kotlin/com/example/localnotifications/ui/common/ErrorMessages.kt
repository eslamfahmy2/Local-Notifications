package com.example.localnotifications.ui.common

import androidx.annotation.StringRes
import com.example.localnotifications.R
import com.example.localnotifications.feature.detail.DetailError
import com.example.localnotifications.feature.list.ListError

/** Maps presentation errors to user facing text. Kept in the UI layer so ViewModels stay Android free. */
@StringRes
fun ListError.messageRes(): Int = when (this) {
    ListError.Network -> R.string.error_network
    ListError.Parsing -> R.string.error_parsing
    ListError.Unknown -> R.string.error_unknown
}

@StringRes
fun DetailError.messageRes(): Int = when (this) {
    DetailError.NotFound -> R.string.error_not_found
    DetailError.Network -> R.string.error_network
    DetailError.Parsing -> R.string.error_parsing
    DetailError.Unknown -> R.string.error_unknown
}
