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
 * Verifies that [ApprovalBanner] renders a prominent inline prompt for
 * `WaitingForApproval` and stays invisible otherwise. The banner is the
 * primary surface for the HITL gate, complementing the compact console line.
 */
class ApprovalBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shouldRenderApproveDenyForWaitingForApproval() {
        var approveCount = 0
        var denyCount = 0

        composeTestRule.setContent {
            ApprovalBanner(
                state = AgentOrchestratorState.WaitingForApproval(
                    toolName = "Calendar",
                    arguments = "{\"date\":\"today\"}",
                    risk = ToolRisk.SENSITIVE,
                ),
                onApprove = { approveCount++ },
                onDeny = { denyCount++ },
            )
        }

        composeTestRule.onNodeWithText("Approve Calendar?").assertIsDisplayed()
        composeTestRule.onNodeWithText("SENS").assertIsDisplayed()
        composeTestRule.onNodeWithText("{\"date\":\"today\"}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approve").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deny").assertIsDisplayed()

        composeTestRule.onNodeWithText("Approve").performClick()
        composeTestRule.onNodeWithText("Deny").performClick()

        assert(approveCount == 1) { "Approve callback should fire once, fired $approveCount" }
        assert(denyCount == 1) { "Deny callback should fire once, fired $denyCount" }
    }

    @Test
    fun shouldRenderDestructiveBadge() {
        composeTestRule.setContent {
            ApprovalBanner(
                state = AgentOrchestratorState.WaitingForApproval(
                    toolName = "PurgeData",
                    arguments = "{}",
                    risk = ToolRisk.DESTRUCTIVE,
                ),
                onApprove = {},
                onDeny = {},
            )
        }

        composeTestRule.onNodeWithText("DEST").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approve PurgeData?").assertIsDisplayed()
    }

    @Test
    fun shouldRenderNothingForNonApprovalState() {
        composeTestRule.setContent {
            ApprovalBanner(
                state = AgentOrchestratorState.Thinking("..."),
                onApprove = {},
                onDeny = {},
            )
        }

        composeTestRule.onNodeWithText("Approve").assertDoesNotExist()
        composeTestRule.onNodeWithText("Deny").assertDoesNotExist()
    }

    @Test
    fun shouldRenderNothingForNullState() {
        composeTestRule.setContent {
            ApprovalBanner(
                state = null,
                onApprove = {},
                onDeny = {},
            )
        }

        composeTestRule.onNodeWithText("Approve").assertDoesNotExist()
        composeTestRule.onNodeWithText("Deny").assertDoesNotExist()
    }
}
