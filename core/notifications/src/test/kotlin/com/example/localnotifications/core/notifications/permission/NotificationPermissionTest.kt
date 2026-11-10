package com.example.localnotifications.core.notifications.permission

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [33])
    fun `android 13 requires the runtime permission`() {
        assertTrue(NotificationPermission.isRuntimePermissionRequired())
        assertFalse(NotificationPermission.isGranted(app))
    }

    @Test
    @Config(sdk = [33])
    fun `granted once the user accepts`() {
        shadowOf(app).grantPermissions(NotificationPermission.PERMISSION)

        assertTrue(NotificationPermission.isGranted(app))
    }

    @Test
    @Config(sdk = [30])
    fun `older versions need no runtime permission`() {
        assertFalse(NotificationPermission.isRuntimePermissionRequired())
        assertTrue(NotificationPermission.isGranted(app))
    }
}
