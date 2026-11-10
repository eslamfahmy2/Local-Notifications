package com.example.localnotifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.localnotifications.core.data.remote.NotificationApi
import com.example.localnotifications.core.notifications.scheduler.WorkManagerNotificationScheduler
import com.example.localnotifications.di.NetworkModule
import com.example.localnotifications.testing.isMenuItemEnabled
import com.example.localnotifications.testing.latestAlertDialog
import com.example.localnotifications.testing.read
import com.example.localnotifications.testing.waitUntil
import com.example.localnotifications.ui.detail.DetailActivity
import com.example.localnotifications.ui.list.MainActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The whole app with only the server faked: real Hilt graph, repository, SAX parser, Retrofit,
 * ViewModels, Activities and WorkManager (test mode, so the delay can be skipped).
 */
@HiltAndroidTest
@UninstallModules(NetworkModule::class)
@RunWith(AndroidJUnit4::class)
class AppFlowIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val server = MockWebServer().apply { start() }

    @BindValue
    @JvmField
    val api: NotificationApi = retrofit2.Retrofit.Builder()
        .baseUrl(server.url("/"))
        .build()
        .create(NotificationApi::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    private val feed = "<xml encoding='UTF-8'><notifications>" +
        "<notification><id>1</id><title>Schedule me after 10 seconds.</title><timeInSeconds>10</timeInSeconds></notification>" +
        "<notification><id>2</id><title>Schedule me after 20 seconds.</title><timeInSeconds>20</timeInSeconds></notification>" +
        "</notifications></xml>"

    @Before
    fun setUp() {
        // Must run before Hilt first asks for WorkManager.getInstance().
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        scenarios.forEach { it.close() }
        server.shutdown()
    }

    private fun launchMain(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenarios += it }

    private fun ActivityScenario<MainActivity>.itemCount(): Int =
        read { it.findViewById<RecyclerView>(R.id.recycler).adapter?.itemCount ?: 0 }

    @Test
    fun `feed to list to detail to scheduled to notification posted`() {
        server.enqueue(MockResponse().setBody(feed))
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // List: loaded from the server and parsed.
        val main = launchMain()
        waitUntil { main.itemCount() == 2 }
        assertEquals("/localalerts.php", server.takeRequest().path)

        // Tap a row; the list screen asks to open the detail screen.
        onView(withText("Schedule me after 20 seconds.")).perform(click())
        val detailIntent = main.read { shadowOf(it).nextStartedActivity }
        assertNotNull(detailIntent)

        // Detail: served from the repository cache, no second request.
        val detail = ActivityScenario.launch<DetailActivity>(detailIntent).also { scenarios += it }
        waitUntil { detail.read { it.findViewById<View>(R.id.contentGroup).visibility == View.VISIBLE } }
        onView(withText("Schedule me after 20 seconds.")).check(matches(isDisplayed()))
        assertEquals(1, server.requestCount)

        // Schedule: real WorkManager work with the item's delay; Cancel becomes available.
        onView(withId(R.id.scheduleButton)).perform(click())
        waitUntil { latestAlertDialog()?.isShowing == true }
        latestAlertDialog()!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        waitUntil { latestAlertDialog()?.isShowing == true }
        val workManager = WorkManager.getInstance(context)
        val work = workManager.getWorkInfosForUniqueWork(WorkManagerNotificationScheduler.workName(2)).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(20_000L, work.initialDelayMillis)
        waitUntil { detail.read { it.isMenuItemEnabled(R.id.action_cancel) } }

        // Delay runs out: the notification is posted and Cancel turns off again.
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(work.id)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val posted = manager.getNotification(2)
        assertNotNull(posted)
        assertEquals(
            "Schedule me after 20 seconds.",
            posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        waitUntil { !detail.read { it.isMenuItemEnabled(R.id.action_cancel) } }
    }

    @Test
    fun `server error shows the network message, Retry loads the list`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val main = launchMain()

        waitUntil { main.read { it.findViewById<View>(R.id.emptyState).visibility == View.VISIBLE } }
        onView(withText(R.string.error_network)).check(matches(isDisplayed()))

        server.enqueue(MockResponse().setBody(feed))
        onView(withId(R.id.retryButton)).perform(click())

        waitUntil { main.itemCount() == 2 }
        onView(withText("Schedule me after 10 seconds.")).check(matches(isDisplayed()))
    }

    @Test
    fun `malformed feed shows the parsing message`() {
        server.enqueue(MockResponse().setBody("<notifications><notification>"))
        val main = launchMain()

        waitUntil { main.read { it.findViewById<View>(R.id.emptyState).visibility == View.VISIBLE } }
        onView(withText(R.string.error_parsing)).check(matches(isDisplayed()))
    }
}
