package ai.agent.android.presentation.ui.chat.legacy

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ToolRisk
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Compose-side tests for [AgentThoughtIndicator]. Phase 17.4 reduced this
 * composable to a single console-styled line (with an inline Approve/Deny
 * action row for `WaitingForApproval`), so the assertions verify the new
 * line-based output rather than the previous expandable card.
 */
class AgentThoughtIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shouldDisplayThinkingLine() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Thinking("Hmm..."))
        }

        composeTestRule.onNodeWithText("[NOW] Agent is thinking...").assertIsDisplayed()
        // The streaming partial text is no longer rendered — it used to drag the
        // chat list and is now hidden behind the static label.
        composeTestRule.onNodeWithText("Hmm...").assertDoesNotExist()
    }

    @Test
    fun shouldDisplayExecutingToolLine() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.ExecutingTool(
                    toolName = "Calendar",
                    arguments = "date=today",
                ),
            )
        }

        composeTestRule.onNodeWithText("[NOW] Using tool: Calendar...").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayObservationLine() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.ObservationResult(
                    toolName = "Calendar",
                    result = "Meeting at 10 AM",
                ),
            )
        }

        composeTestRule.onNodeWithText("[NOW] Observation: Calendar").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayAnsweringLine() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Answering("Final answer text"))
        }

        composeTestRule.onNodeWithText("[NOW] Agent is answering...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Final answer text").assertDoesNotExist()
    }

    @Test
    fun shouldRenderApproveDenyForWaitingForApproval() {
        var approveCount = 0
        var denyCount = 0

        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.WaitingForApproval(
                    toolName = "Calendar",
                    arguments = "date=today",
                    risk = ToolRisk.SENSITIVE,
                ),
                onApprove = { approveCount++ },
                onDeny = { denyCount++ },
            )
        }

        composeTestRule.onNodeWithText("[ASK] Approve Calendar?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approve").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deny").assertIsDisplayed()
        composeTestRule.onNodeWithText("SENS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Approve").performClick()
        composeTestRule.onNodeWithText("Deny").performClick()

        assert(approveCount == 1) { "Approve callback should fire once, fired $approveCount" }
        assert(denyCount == 1) { "Deny callback should fire once, fired $denyCount" }
    }

    @Test
    fun shouldRenderDestructiveRiskChip() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.WaitingForApproval(
                    toolName = "PurgeData",
                    arguments = "{}",
                    risk = ToolRisk.DESTRUCTIVE,
                ),
            )
        }

        composeTestRule.onNodeWithText("DEST").assertIsDisplayed()
    }

    @Test
    fun shouldRenderReadOnlyRiskChip() {
        composeTestRule.setContent {
            AgentThoughtIndicator(
                state = AgentOrchestratorState.WaitingForApproval(
                    toolName = "Search",
                    arguments = "q=test",
                    risk = ToolRisk.READ_ONLY,
                ),
            )
        }

        composeTestRule.onNodeWithText("READ").assertIsDisplayed()
    }

    @Test
    fun shouldRenderNothingForCompletedState() {
        composeTestRule.setContent {
            AgentThoughtIndicator(state = AgentOrchestratorState.Completed("Done"))
        }

        composeTestRule.onNodeWithText("[NOW] Agent is thinking...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Done").assertDoesNotExist()
    }
}
