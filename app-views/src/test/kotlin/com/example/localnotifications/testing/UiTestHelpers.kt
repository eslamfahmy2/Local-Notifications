package com.example.localnotifications.testing

import android.app.Activity
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import com.example.localnotifications.R
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** Runs pending main-thread work (coroutines resumed on Dispatchers.Main, posted UI updates). */
fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

/** Reads a value from the activity on the main thread. */
fun <A : Activity, T> ActivityScenario<A>.read(block: (A) -> T): T {
    var value: Result<T>? = null
    onActivity { value = runCatching { block(it) } }
    return value!!.getOrThrow()
}

/**
 * For tests with real background threads (OkHttp, Dispatchers.IO): keeps draining the main
 * looper until [condition] holds, or fails after [timeoutMs].
 */
fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        idleMain()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
        Thread.sleep(10)
    }
}

/** Selects a toolbar menu item the way a tap does. Returns false if the item is disabled. */
fun Activity.clickMenuItem(id: Int): Boolean =
    findViewById<Toolbar>(R.id.toolbar).menu.performIdentifierAction(id, 0)

fun Activity.isMenuItemEnabled(id: Int): Boolean =
    findViewById<Toolbar>(R.id.toolbar).menu.findItem(id).isEnabled

fun latestAlertDialog(): AlertDialog? = ShadowDialog.getLatestDialog() as? AlertDialog
