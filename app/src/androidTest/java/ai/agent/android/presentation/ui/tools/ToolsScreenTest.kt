package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpConnectionStatus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ToolsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testToolsScreen_displaysToolsAndMcpServers() {
        val mockViewModel = mockk<ToolsViewModel>(relaxed = true)
        val fakeState = MutableStateFlow(
            ToolsUiState(
                localTools = listOf(AgentTool("MockTool", "MockDescription", "{}")),
                mcpServers = listOf(
                    McpServerSnapshot(
                        url = "http://mockserver.com",
                        status = McpConnectionStatus.Connected,
                        tools = emptyList(),
                    ),
                ),
                disabledAppFunctions = setOf(),
            ),
        )
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ToolsScreen(viewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("MockTool").assertIsDisplayed()
        composeTestRule.onNodeWithText("MockDescription").assertIsDisplayed()
        composeTestRule.onNodeWithText("http://mockserver.com").assertIsDisplayed()
    }
}
