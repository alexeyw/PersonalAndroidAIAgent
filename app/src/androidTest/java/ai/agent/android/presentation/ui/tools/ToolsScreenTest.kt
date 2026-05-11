package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
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
        // Mock the ViewModel
        val mockViewModel = mockk<ToolsViewModel>(relaxed = true)

        // Prepare fake state
        val fakeState = MutableStateFlow(
            ToolsUiState(
                localTools = listOf(AgentTool("MockTool", "MockDescription", "{}")),
                mcpServers = listOf("http://mockserver.com"),
                disabledAppFunctions = setOf(),
            ),
        )

        every { mockViewModel.uiState } returns fakeState

        // Launch UI
        composeTestRule.setContent {
            ToolsScreen(viewModel = mockViewModel)
        }

        // Verify elements
        composeTestRule.onNodeWithText("MockTool").assertIsDisplayed()
        composeTestRule.onNodeWithText("MockDescription").assertIsDisplayed()
        composeTestRule.onNodeWithText("http://mockserver.com").assertIsDisplayed()
    }
}
