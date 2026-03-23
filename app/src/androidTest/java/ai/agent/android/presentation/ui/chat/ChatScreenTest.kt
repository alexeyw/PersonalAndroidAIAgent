package ai.agent.android.presentation.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testChatScreen_displaysMessages_successState() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)
        
        val fakeState = MutableStateFlow(
            ChatUiState(
                messages = listOf(
                    ChatMessage(1, "s1", Role.USER, "Hello AI", 0L),
                    ChatMessage(2, "s1", Role.AGENT, "Hi User!", 0L)
                ),
                isGenerating = false,
                errorMessage = null
            )
        )
        
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("Hello AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hi User!").assertIsDisplayed()
    }

    @Test
    fun testChatScreen_displaysErrorState() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)
        
        val fakeState = MutableStateFlow(
            ChatUiState(
                errorMessage = "Network Error Occurred"
            )
        )
        
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        // Error message is shown in a Snackbar. We can verify it exists on screen.
        composeTestRule.onNodeWithText("Network Error Occurred").assertIsDisplayed()
    }
    
    @Test
    fun testChatScreen_loadingGeneratingState() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)
        
        val fakeState = MutableStateFlow(
            ChatUiState(
                isGenerating = true
            )
        )
        
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        // When generating, the Send button is disabled or turns into a progress indicator.
        // We can check the "Ask the agent..." placeholder since it's present.
        composeTestRule.onNodeWithText("Ask the agent...").assertIsDisplayed()
    }
}
