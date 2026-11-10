package com.example.localnotifications.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Adds the system bar and display cutout insets to this view's existing padding, so content
 * is not hidden behind the navigation bar or side cutouts when drawing edge to edge.
 * The top inset is handled by the AppBarLayout.
 */
fun View.applySystemBarInsets() {
    val initialLeft = paddingLeft
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(
            left = initialLeft + bars.left,
            right = initialRight + bars.right,
            bottom = initialBottom + bars.bottom,
        )
        windowInsets
    }
}
