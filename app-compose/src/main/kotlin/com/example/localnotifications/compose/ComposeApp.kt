package com.example.localnotifications.compose

import android.app.Application
import com.example.localnotifications.core.notifications.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ComposeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).ensureChannel()
    }
}
