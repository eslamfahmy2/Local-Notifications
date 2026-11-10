package com.example.localnotifications.core.notifications.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemManager = context.getSystemService(NotificationManager::class.java)
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        helper = NotificationHelper(context)
    }

    @Test
    fun `ensureChannel creates the channel`() {
        helper.ensureChannel()

        assertNotNull(systemManager.getNotificationChannel(NotificationHelper.CHANNEL_ID))
    }

    @Test
    fun `show posts a notification with the given title`() {
        assertTrue(helper.show(id = 7, title = "Hello"))

        val posted = shadowOf(systemManager).allNotifications.single()
        assertEquals("Hello", posted.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `show posts nothing when notifications are disabled`() {
        shadowOf(systemManager).setNotificationsEnabled(false)

        assertFalse(helper.show(id = 7, title = "Hello"))
        assertTrue(shadowOf(systemManager).allNotifications.isEmpty())
    }

    @Test
    fun `same id replaces the earlier notification`() {
        helper.show(id = 1, title = "First")
        helper.show(id = 1, title = "Second")

        assertEquals(1, shadowOf(systemManager).allNotifications.size)
    }
}
