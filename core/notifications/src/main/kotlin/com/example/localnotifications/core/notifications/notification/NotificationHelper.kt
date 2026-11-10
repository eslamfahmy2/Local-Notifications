package com.example.localnotifications.core.notifications.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.localnotifications.core.notifications.R

/**
 * Owns the notification channel and builds/posts notifications.
 * Tapping a notification just launches the app (or brings it to the front if it is open).
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Safe to call repeatedly. Call once at startup so the channel shows in system settings. */
    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .build()
        manager.createNotificationChannel(channel)
    }

    /** @return true if the notification was posted, false if notifications are disabled/denied. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the Android 13+ permission.
    fun show(id: Int, title: String): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_local_notification)
            .setContentTitle(title)
            .setContentIntent(launchAppIntent())
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
        return true
    }

    private fun launchAppIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "local_alerts"
    }
}
