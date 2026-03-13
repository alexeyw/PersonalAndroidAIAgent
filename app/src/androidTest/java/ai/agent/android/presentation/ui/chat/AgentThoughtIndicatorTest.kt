package ai.agent.android.presentation.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ai.agent.android.domain.models.AgentOrchestratorState
import org.junit.Rule
import org.junit.Test

class AgentThoughtIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shouldDisplayThinkingState() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Thinking("Hmm..."))
        }

        composeTestRule.onNodeWithText("Agent is thinking...").assertIsDisplayed()
        
        // Expand the card
        composeTestRule.onNodeWithText("Agent is thinking...").performClick()
        
        // Check partial text
        composeTestRule.onNodeWithText("Hmm...").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayExecutingToolState() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.ExecutingTool(toolName = "Calendar", arguments = "date=today")
            )
        }

        composeTestRule.onNodeWithText("Using tool: Calendar...").assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Using tool: Calendar...").performClick()
        
        composeTestRule.onNodeWithText("Arguments: date=today").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayObservationResultState() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.ObservationResult(toolName = "Calendar", result = "Meeting at 10 AM")
            )
        }

        composeTestRule.onNodeWithText("Observation received...").assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Observation received...").performClick()
        
        composeTestRule.onNodeWithText("Result from Calendar:\nMeeting at 10 AM").assertIsDisplayed()
    }
    
    @Test
    fun shouldDisplayAnsweringStateAsMarkdown() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Answering("Final answer text"))
        }

        composeTestRule.onNodeWithText("Final answer text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Agent is thinking...").assertDoesNotExist()
    }
    
    @Test
    fun shouldNotDisplayAnythingForCompletedState() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Completed("Done"))
        }

        composeTestRule.onNodeWithText("Agent is thinking...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Done").assertDoesNotExist()
    }
}
