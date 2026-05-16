package ai.agent.android.presentation.ui.chat.legacy

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for [ClarificationCard].
 *
 * Covers all three lifecycle states (PENDING with options, PENDING free-form,
 * ANSWERED, TIMED_OUT) and verifies that user interactions invoke the supplied
 * lambdas with the expected payloads.
 */
class ClarificationCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingWithOptions_rendersQuestionAndButtons() {
        val model = pendingModel(options = listOf("Yes", "No"))

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = {}, onTimeout = {})
        }

        composeTestRule.onNodeWithText("Agent requests clarification").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yes").assertIsDisplayed()
        composeTestRule.onNodeWithText("No").assertIsDisplayed()
    }

    @Test
    fun pendingWithOptions_clickingOption_invokesOnAnswerWithLabel() {
        var captured: String? = null
        val model = pendingModel(options = listOf("Yes", "No"))

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = { captured = it }, onTimeout = {})
        }

        composeTestRule.onNodeWithContentDescription("Option: Yes").performClick()

        assertEquals("Yes", captured)
    }

    @Test
    fun pendingFreeForm_typingAndSending_invokesOnAnswerWithTrimmedText() {
        var captured: String? = null
        val model = pendingModel(options = null)

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = { captured = it }, onTimeout = {})
        }

        composeTestRule.onNodeWithTag("ClarificationInput").performTextInput("  hello  ")
        composeTestRule.onNodeWithContentDescription("Send answer").performClick()

        assertEquals("hello", captured)
    }

    @Test
    fun pendingFreeForm_blankInput_doesNotInvokeOnAnswer() {
        var captured: String? = null
        val model = pendingModel(options = null)

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = { captured = it }, onTimeout = {})
        }

        // Send button is disabled when text is blank, so clicking is a no-op.
        composeTestRule.onNodeWithContentDescription("Send answer").performClick()

        assertNull(captured)
    }

    @Test
    fun answeredState_rendersCompactSummary() {
        val model = pendingModel(options = listOf("A", "B"))
            .copy(status = ClarificationCardUiModel.Status.ANSWERED, answer = "A")

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = {}, onTimeout = {})
        }

        composeTestRule.onNodeWithTag("ClarificationAnswered").assertIsDisplayed()
        composeTestRule.onNodeWithText("You answered: A").assertIsDisplayed()
        // No interactive controls should be shown.
        composeTestRule.onNodeWithContentDescription("Send answer").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Option: A").assertDoesNotExist()
    }

    @Test
    fun timedOutState_rendersDefaultAnswerLabel() {
        val model = pendingModel(options = listOf("A", "B"))
            .copy(status = ClarificationCardUiModel.Status.TIMED_OUT, answer = "A")

        composeTestRule.setContent {
            ClarificationCard(model = model, onAnswer = {}, onTimeout = {})
        }

        composeTestRule.onNodeWithTag("ClarificationTimedOut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default answer used: A").assertIsDisplayed()
    }

    private fun pendingModel(options: List<String>?): ClarificationCardUiModel = ClarificationCardUiModel(
        id = "card-${System.nanoTime()}",
        question = "Continue?",
        options = options,
        timeoutMs = 60_000L,
        startedAtMs = SystemClock.uptimeMillis(),
    )
}
