package com.example.localnotifications.core.devicetesting

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

val appContext: Context get() = ApplicationProvider.getApplicationContext()

/** Polls [condition] until true. For work Espresso cannot wait on (WorkManager, Room, the system UI). */
fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(100)
    }
}

/** True for a [check] that passes, false if it throws (e.g. an Espresso assertion). */
fun passes(check: () -> Unit): Boolean = runCatching(check).isSuccess

/**
 * Brings the existing [activity] back to the front after the app went to the background,
 * like reopening it from recents (it restarts, it is not recreated).
 *
 * Uses the shell because Android 10+ blocks a backgrounded app from starting activities itself,
 * which is why ActivityScenario.moveToState(RESUMED) gets stuck after pressing Home.
 */
fun bringToFront(activity: Class<out Activity>) {
    val reorderToFront = "0x%x".format(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    device.executeShellCommand("am start -W -n ${appContext.packageName}/${activity.name} -f $reorderToFront")
}

/**
 * Reopens the app like tapping its launcher icon: the task comes back with whatever screen was on
 * top (e.g. the detail screen), unlike [bringToFront] which reorders a given screen to the top.
 */
fun reopenApp() {
    val launcher = checkNotNull(appContext.packageManager.getLaunchIntentForPackage(appContext.packageName))
    val flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    device.executeShellCommand(
        "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-f ${"0x%x".format(flags)} -n ${launcher.component!!.flattenToShortString()}",
    )
}

fun hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Runs [action] (a tap on "Schedule"). If the app did not have the notification permission yet,
 * asserts that the real system dialog appeared and taps Allow on it.
 *
 * Gradle installs the app fresh for each run, so the first test that schedules sees the dialog.
 */
fun allowingNotificationPermission(action: () -> Unit) {
    val askedBefore = !hasNotificationPermission()
    action()
    if (!askedBefore) return

    val allow = device.wait(
        Until.findObject(By.res(Pattern.compile(".*:id/permission_allow_button"))),
        5_000,
    )
    checkNotNull(allow) { "The notification permission dialog did not appear" }
    allow.click()
    waitFor { hasNotificationPermission() }
}
