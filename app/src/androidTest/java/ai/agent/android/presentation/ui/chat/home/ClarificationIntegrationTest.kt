package ai.agent.android.presentation.ui.chat.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chat.ClarificationCardModel
import app.knotwork.design.screens.chat.ChatHomeCallbacks
import app.knotwork.design.screens.chat.ChatHomeContent
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeViewState
import app.knotwork.design.screens.chat.ChatHomeVisualState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests covering the chat-home clarification card. Ports
 * the legacy [ai.agent.android.presentation.ui.chat.legacy.ClarificationCardTest]
 * happy paths onto the Phase 22 chat-home surface.
 */
class ClarificationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingWithOptions_rendersQuestionAndQuickReplyChips() {
        composeTestRule.setContent {
            ChatHomeContent(
                state = viewStateWithClarification(
                    question = "Which calendar should I use?",
                    quickReplies = listOf("Work", "Personal"),
                ),
                callbacks = ChatHomeCallbacks(),
            )
        }
        composeTestRule.onNodeWithText("Which calendar should I use?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Personal").assertIsDisplayed()
    }

    @Test
    fun quickReplyChip_click_dispatchesOnClarificationReplyWithLabel() {
        var captured: String? = null
        composeTestRule.setContent {
            ChatHomeContent(
                state = viewStateWithClarification(
                    question = "Pick a colour",
                    quickReplies = listOf("Red", "Blue"),
                ),
                callbacks = ChatHomeCallbacks(
                    onClarificationReply = { captured = it },
                ),
            )
        }
        composeTestRule.onNodeWithText("Blue").performClick()
        assertEquals("Blue", captured)
    }

    private fun viewStateWithClarification(question: String, quickReplies: List<String>): ChatHomeViewState {
        val row = ChatHomeMessageRow(
            id = "a-clar",
            role = ChatRole.Assistant,
            content = ChatContent.Clarification(
                model = ClarificationCardModel(
                    question = question,
                    quickReplies = quickReplies,
                ),
            ),
            metadata = ChatMetadata(timestamp = "09:16", model = "TestModel"),
        )
        return ChatHomeViewState(
            visualState = ChatHomeVisualState.Clarification,
            threadTitle = "Test thread",
            modelName = "TestModel",
            messages = listOf(row),
        )
    }
}
