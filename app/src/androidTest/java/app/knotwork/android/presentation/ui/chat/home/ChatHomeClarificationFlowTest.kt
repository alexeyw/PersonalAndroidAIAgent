package app.knotwork.android.presentation.ui.chat.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.knotwork.android.domain.models.ClarificationRequest
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/**
 * Covers the UI-side Clarification flow at the
 * [ChatHomeScreen] boundary.
 *
 * The 5-second timeout watchdog that auto-cancels a pending clarification
 * runs inside [ChatHomeViewModel] via `delay(timeoutMs)` and cannot be
 * exercised through `mainClock.advanceTimeBy` — the Compose mainClock
 * drives recomposition only, not coroutine `delay`. That contract is
 * verified by `ChatHomeViewModelClarificationTest` in the JVM source set;
 * here we restrict ourselves to the UI consequence: when the VM clears
 * the pending request and flips state back to Idle, the clarification
 * card disappears.
 */
class ChatHomeClarificationFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clarificationState_rendersQuestionFromPendingRequest() {
        val (viewModel, _) = mockChatHomeViewModel(
            initialState = ChatHomeUiState.Clarification,
            initialPendingClarification = ClarificationRequest(
                id = "clar-1",
                question = "Which calendar should I use for the rollout sync?",
                options = listOf("Work", "Personal"),
                timeoutMs = 5_000L,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { ChatHomeScreen(viewModel = viewModel) }
        }

        composeTestRule
            .onNodeWithText("Which calendar should I use for the rollout sync?")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Personal").assertIsDisplayed()
    }

    @Test
    fun quickReplyTap_dispatchesSubmitClarificationReplyWithLabel() {
        val (viewModel, _) = mockChatHomeViewModel(
            initialState = ChatHomeUiState.Clarification,
            initialPendingClarification = ClarificationRequest(
                id = "clar-2",
                question = "Pick a colour",
                options = listOf("Red", "Blue"),
                timeoutMs = 5_000L,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { ChatHomeScreen(viewModel = viewModel) }
        }

        composeTestRule.onNodeWithText("Blue").performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { viewModel.submitClarificationReply("Blue") }
    }

    @Test
    fun clarificationCleared_questionRowDisappears() {
        val (viewModel, handles) = mockChatHomeViewModel(
            initialState = ChatHomeUiState.Clarification,
            initialPendingClarification = ClarificationRequest(
                id = "clar-3",
                question = "Pick a calendar for the rollout",
                options = listOf("Work", "Personal"),
                timeoutMs = 5_000L,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { ChatHomeScreen(viewModel = viewModel) }
        }

        composeTestRule.onNodeWithText("Pick a calendar for the rollout").assertIsDisplayed()

        // Simulate the VM-side watchdog (or a successful submit): the VM
        // clears `pendingClarification` and flips state back to Idle. The
        // clarification card must drop out of the tree.
        handles.pendingClarificationFlow.value = null
        handles.stateFlow.value = ChatHomeUiState.Idle
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Pick a calendar for the rollout").assertDoesNotExist()
    }
}
