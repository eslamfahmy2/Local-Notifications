package com.example.localnotifications.compose.ui.detail

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.compose.R
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.feature.detail.DetailError
import com.example.localnotifications.feature.detail.DetailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The stateless detail screen: given a state, what is drawn and which callbacks taps trigger. */
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val item = NotificationItem(4, "Schedule me after 40 seconds.", 40)
    private val calls = mutableListOf<String>()

    private fun show(state: DetailState) {
        composeRule.setContent {
            LocalNotificationsTheme {
                DetailScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onBack = { calls += "back" },
                    onSchedule = { calls += "schedule" },
                    onCancel = { calls += "cancel" },
                    onRetry = { calls += "retry" },
                )
            }
        }
    }

    private fun text(id: Int) = context.getString(id)

    private fun cancelButton() =
        composeRule.onNode(hasText(text(R.string.action_cancel)) and hasClickAction())

    private val loaded = DetailState(isLoading = false, notification = item)

    @Test
    fun loading_showsProgress() {
        show(DetailState(isLoading = true))

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).assertDoesNotExist()
    }

    @Test
    fun loaded_showsTitle_andScheduleCallsBack() {
        show(loaded)

        composeRule.onNodeWithText(item.title).assertExists()
        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).performClick()

        assertEquals(listOf("schedule"), calls)
    }

    @Test
    fun cancel_isDisabledWhileNothingIsScheduled() {
        show(loaded.copy(isScheduled = false))

        cancelButton().assertIsNotEnabled()
    }

    @Test
    fun cancel_isEnabledWhileScheduled_andCallsBack() {
        show(loaded.copy(isScheduled = true))

        cancelButton().performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun error_showsMessageInsteadOfContent_andRetryCallsBack() {
        show(DetailState(isLoading = false, error = DetailError.NotFound))

        composeRule.onNodeWithText(text(R.string.error_not_found)).assertExists()
        composeRule.onNodeWithText(text(R.string.schedule_this_notification)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun backArrow_callsBack() {
        show(loaded)

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun portrait_putsTheButtonBelowTheTitle() {
        show(loaded)

        val title = composeRule.onNodeWithText(item.title).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.schedule_this_notification)).getUnclippedBoundsInRoot()
        assertTrue("button should start below the title", button.top >= title.bottom)
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun landscape_putsTheButtonBesideTheTitle() {
        show(loaded)

        val title = composeRule.onNodeWithText(item.title).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.schedule_this_notification)).getUnclippedBoundsInRoot()
        assertTrue("button should sit to the right of the title", button.left >= title.right)
        assertTrue("button and title should share a row", button.top < title.bottom && title.top < button.bottom)
    }
}
