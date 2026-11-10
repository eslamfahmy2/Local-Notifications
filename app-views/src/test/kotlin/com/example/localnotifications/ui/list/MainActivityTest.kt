package com.example.localnotifications.ui.list

import android.content.Context
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.R
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.di.DataModule
import com.example.localnotifications.di.NotificationsModule
import com.example.localnotifications.feature.detail.DetailArgs
import com.example.localnotifications.core.testing.FakeNotificationRepository
import com.example.localnotifications.core.testing.FakeNotificationScheduler
import com.example.localnotifications.testing.clickMenuItem
import com.example.localnotifications.testing.idleMain
import com.example.localnotifications.testing.read
import com.example.localnotifications.ui.detail.DetailActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@UninstallModules(DataModule::class, NotificationsModule::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val repository = FakeNotificationRepository()
    private val scheduler = FakeNotificationScheduler()

    @BindValue
    @JvmField
    val boundRepository: NotificationRepository = repository

    @BindValue
    @JvmField
    val boundScheduler: NotificationScheduler = scheduler

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = listOf(NotificationItem(1, "First", 10), NotificationItem(2, "Second", 20))
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = hiltRule.inject()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenario = it
            idleMain()
        }

    private fun ActivityScenario<MainActivity>.visibilityOf(id: Int): Int = read { it.findViewById<View>(id).visibility }

    /** Stop and start again without the app leaving the foreground, as when the detail screen covers the list. */
    private fun ActivityScenario<MainActivity>.coverAndReturn() {
        moveToState(Lifecycle.State.CREATED)
        moveToState(Lifecycle.State.RESUMED)
        idleMain()
    }

    /** The user leaves the whole app (Home) and comes back, as ProcessLifecycleOwner reports it. */
    private fun ActivityScenario<MainActivity>.backgroundAndReturn() {
        val app = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
        moveToState(Lifecycle.State.CREATED)
        app.currentState = Lifecycle.State.STARTED
        app.currentState = Lifecycle.State.CREATED // app-wide ON_STOP
        moveToState(Lifecycle.State.RESUMED)
        app.currentState = Lifecycle.State.RESUMED
        idleMain()
    }

    @Test
    fun `shows progress while loading, then the list`() {
        repository.result = Result.success(items)
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }

        val scenario = launch()
        assertEquals(View.VISIBLE, scenario.visibilityOf(R.id.progress))

        gate.complete(Unit)
        idleMain()

        assertEquals(View.INVISIBLE, scenario.visibilityOf(R.id.progress))
        onView(withText("First")).check(matches(isDisplayed()))
        onView(withText("Second")).check(matches(isDisplayed()))
        onView(withText(context.getString(R.string.item_delay, "20 seconds"))).check(matches(isDisplayed()))
        assertEquals(View.GONE, scenario.visibilityOf(R.id.emptyState))
    }

    @Test
    fun `first load failure shows the error and Retry recovers`() {
        repository.result = Result.failure(DomainError.Network())
        launch()

        onView(withText(R.string.error_network)).check(matches(isDisplayed()))
        onView(withId(R.id.retryButton)).check(matches(isDisplayed()))

        repository.result = Result.success(items)
        onView(withId(R.id.retryButton)).perform(click())
        idleMain()

        onView(withText("First")).check(matches(isDisplayed()))
        onView(withId(R.id.emptyState)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `parsing failure shows its own message`() {
        repository.result = Result.failure(DomainError.Parsing())
        launch()

        onView(withText(R.string.error_parsing)).check(matches(isDisplayed()))
    }

    @Test
    fun `empty feed shows the empty message`() {
        repository.result = Result.success(emptyList())
        launch()

        onView(withText(R.string.empty_list)).check(matches(isDisplayed()))
        onView(withId(R.id.errorBanner)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `failed reload keeps the list and shows a banner that retries`() {
        repository.result = Result.success(items)
        val scenario = launch()

        repository.result = Result.failure(DomainError.Network())
        scenario.backgroundAndReturn()

        val bannerText = context.getString(R.string.error_banner, context.getString(R.string.error_network))
        onView(withId(R.id.errorBanner)).check(matches(withText(bannerText)))
        onView(withId(R.id.errorBanner)).check(matches(isDisplayed()))
        onView(withText("First")).check(matches(isDisplayed()))

        repository.result = Result.success(items)
        onView(withId(R.id.errorBanner)).perform(click())
        idleMain()

        onView(withId(R.id.errorBanner)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `rotation does not reload`() {
        repository.result = Result.success(items)
        val scenario = launch()

        scenario.recreate()
        idleMain()

        assertEquals(1, repository.listCalls)
        onView(withText("First")).check(matches(isDisplayed()))
    }

    @Test
    fun `returning from the detail screen does not reload`() {
        repository.result = Result.success(items)
        val scenario = launch()

        scenario.coverAndReturn()

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `returning from the background reloads`() {
        repository.result = Result.success(items)
        val scenario = launch()

        scenario.backgroundAndReturn()

        assertEquals(2, repository.listCalls)
    }

    @Test
    fun `tapping a row opens the detail screen for that notification`() {
        repository.result = Result.success(items)
        val scenario = launch()

        onView(withText("Second")).perform(click())
        idleMain()

        val started = scenario.read { shadowOf(it).nextStartedActivity }
        assertEquals(DetailActivity::class.java.name, started.component?.className)
        assertEquals(2, started.getIntExtra(DetailArgs.NOTIFICATION_ID, -1))
    }

    @Test
    fun `Cancel All cancels every pending notification and confirms`() {
        repository.result = Result.success(items)
        items.forEach(scheduler::schedule)
        val scenario = launch()

        assertTrue(scenario.read { it.clickMenuItem(R.id.action_cancel_all) })
        idleMain()

        assertTrue(scheduler.pending.isEmpty())
        onView(withText(R.string.all_cancelled)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }
}
