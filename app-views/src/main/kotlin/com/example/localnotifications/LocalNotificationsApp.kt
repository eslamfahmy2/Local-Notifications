package com.example.localnotifications

import android.app.Application
import com.example.localnotifications.core.notifications.notification.NotificationHelper
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LocalNotificationsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Creating the channel at startup makes it visible in system settings right away.
        NotificationHelper(this).ensureChannel()
    }
}
