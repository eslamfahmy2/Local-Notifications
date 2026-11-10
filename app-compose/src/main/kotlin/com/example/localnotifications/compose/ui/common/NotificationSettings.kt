package com.example.localnotifications.compose.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens this app's notification settings screen. */
fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
