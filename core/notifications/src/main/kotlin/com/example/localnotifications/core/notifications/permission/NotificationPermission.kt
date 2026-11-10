package com.example.localnotifications.core.notifications.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Shared knowledge about the notification runtime permission, so both UI modules
 * (Views and Compose) ask for it the same way.
 */
object NotificationPermission {

    const val PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    /** Only Android 13+ (API 33) asks the user; earlier versions grant it implicitly. */
    fun isRuntimePermissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !isRuntimePermissionRequired() ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
