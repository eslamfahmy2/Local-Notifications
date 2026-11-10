package com.example.localnotifications.compose.ui.list

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localnotifications.compose.R
import com.example.localnotifications.compose.ui.theme.LocalNotificationsTheme
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.feature.list.ListError
import com.example.localnotifications.feature.list.ListIntent
import com.example.localnotifications.feature.list.ListState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless list screen: given a state, what is drawn and which intents taps send. */
@RunWith(AndroidJUnit4::class)
class ListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = listOf(NotificationItem(1, "First", 10), NotificationItem(2, "Second", 20))
    private val sent = mutableListOf<ListIntent>()

    private fun show(state: ListState) {
        composeRule.setContent {
            LocalNotificationsTheme {
                ListScreen(state = state, snackbarHostState = SnackbarHostState(), onIntent = { sent += it })
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val progress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun loading_showsProgressAndNoEmptyState() {
        show(ListState(isLoading = true))

        composeRule.onNode(progress).assertExists()
        composeRule.onNodeWithText(text(R.string.empty_list)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_retry)).assertDoesNotExist()
    }

    @Test
    fun items_showTitleAndDelay() {
        show(ListState(items = items))

        composeRule.onNodeWithText("First").assertExists()
        composeRule.onNodeWithText("Second").assertExists()
        composeRule.onNodeWithText(text(R.string.item_delay, "20 seconds")).assertExists()
        composeRule.onNode(progress).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.empty_list)).assertDoesNotExist()
    }

    @Test
    fun oneSecondUsesTheSingularForm() {
        show(ListState(items = listOf(NotificationItem(3, "Quick", 1))))

        composeRule.onNodeWithText(text(R.string.item_delay, "1 second")).assertExists()
    }

    @Test
    fun emptyFeed_showsEmptyMessage_andRetryReloads() {
        show(ListState(items = emptyList()))

        composeRule.onNodeWithText(text(R.string.empty_list)).assertExists()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()

        assertEquals(listOf(ListIntent.Reload), sent)
    }

    @Test
    fun errorWithNoItems_showsItsMessage_andRetryReloads() {
        show(ListState(error = ListError.Network))

        composeRule.onNodeWithText(text(R.string.error_network)).assertExists()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()

        assertEquals(listOf(ListIntent.Reload), sent)
    }

    @Test
    fun errorWithItems_keepsTheList_andTheBannerReloads() {
        show(ListState(items = items, error = ListError.Parsing))

        composeRule.onNodeWithText("First").assertExists()
        val banner = text(R.string.error_banner, text(R.string.error_parsing))
        composeRule.onNodeWithText(banner).performClick()

        assertEquals(listOf(ListIntent.Reload), sent)
    }

    @Test
    fun tappingACard_sendsItemClickedWithItsId() {
        show(ListState(items = items))

        composeRule.onNodeWithText("Second").performClick()

        assertEquals(listOf(ListIntent.ItemClicked(2)), sent)
    }

    @Test
    fun cancelAll_sendsCancelAllClicked() {
        show(ListState(items = items))

        composeRule.onNodeWithText(text(R.string.action_cancel_all)).performClick()

        assertEquals(listOf(ListIntent.CancelAllClicked), sent)
    }
}
