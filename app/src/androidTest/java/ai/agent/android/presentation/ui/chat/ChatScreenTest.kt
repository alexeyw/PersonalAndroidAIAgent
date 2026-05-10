package ai.agent.android.presentation.ui.chat

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
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
                    ChatMessage(2, "s1", Role.AGENT, "Hi User!", 0L),
                ),
                isGenerating = false,
                errorMessage = null,
            ),
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
                errorMessage = "Network Error Occurred",
            ),
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
                isGenerating = true,
            ),
        )

        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        // When generating, the Send button is disabled or turns into a progress indicator.
        // We can check the "Ask the agent..." placeholder since it's present.
        composeTestRule.onNodeWithText("Ask the agent...").assertIsDisplayed()
    }

    /**
     * Phase 17.6: the chat input and the collapsed-console strip are stacked
     * inside the Scaffold's `bottomBar`, so when the agent is generating and
     * the console log already has events both surfaces must be visible at the
     * same time without one occluding the other.
     */
    @Test
    fun testChatScreen_bottomBar_rendersInputAndConsoleTogether() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)

        val fakeState = MutableStateFlow(
            ChatUiState(
                isGenerating = true,
                consoleLines = listOf(
                    ConsoleEvent(
                        timestamp = 1_700_000_000_000L,
                        type = ConsoleEventType.NodeExecution,
                        message = "console-line-marker",
                    ),
                ),
            ),
        )
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        // Input bar still visible (placeholder is unique to ChatInputBar).
        composeTestRule.onNodeWithText("Ask the agent...").assertIsDisplayed()
        // Collapsed console row carries the event message verbatim.
        composeTestRule
            .onNodeWithText("console-line-marker", substring = true)
            .assertIsDisplayed()
    }

    /**
     * Phase 17.6: the LazyColumn body uses `paddingValues` from the Scaffold
     * as `contentPadding`, so the latest message must remain visible above
     * the bottomBar — i.e. it isn't clipped by the input + console stack.
     */
    @Test
    fun testChatScreen_lastMessageVisibleAboveBottomBar() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)

        val messages = (1..15).flatMap { i ->
            listOf(
                ChatMessage(i.toLong() * 2, "s1", Role.USER, "user-$i", i.toLong()),
                ChatMessage(i.toLong() * 2 + 1, "s1", Role.AGENT, "agent-$i", i.toLong()),
            )
        }
        val fakeState = MutableStateFlow(
            ChatUiState(
                messages = messages,
                isGenerating = false,
            ),
        )
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            ChatScreen(viewModel = mockViewModel)
        }

        // The very last bubble is what ends up just above the input bar; if
        // the bottomBar overlapped the body it would not register as
        // "displayed".
        composeTestRule.onNodeWithText("agent-15").assertIsDisplayed()
        // And the input bar itself is still on-screen.
        composeTestRule.onNodeWithText("Ask the agent...").assertIsDisplayed()
    }

    /**
     * Phase 17.6: on a short viewport (≤ 480dp tall) the collapsed console
     * drops to a single slot. With three queued events, only the freshest
     * one is rendered; the older two are clipped by the slot budget.
     *
     * The compact branch is keyed on `LocalConfiguration.screenHeightDp`,
     * which `DeviceConfigurationOverride.ForcedSize` does **not** mutate
     * (it only constrains the rendered DpSize). To reliably exercise the
     * branch we publish a Configuration with the boundary value (480dp)
     * via `CompositionLocalProvider(LocalConfiguration provides ...)`.
     */
    @Test
    fun testChatScreen_compactScreen_collapsesConsoleToSingleSlot() {
        val mockViewModel = mockk<ChatViewModel>(relaxed = true)

        val fakeState = MutableStateFlow(
            ChatUiState(
                isGenerating = true,
                consoleLines = listOf(
                    ConsoleEvent(1L, ConsoleEventType.NodeExecution, "first-event"),
                    ConsoleEvent(2L, ConsoleEventType.NodeExecution, "second-event"),
                    ConsoleEvent(3L, ConsoleEventType.NodeExecution, "third-event"),
                ),
            ),
        )
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            val baseConfiguration = LocalConfiguration.current
            val compactConfiguration = Configuration(baseConfiguration).apply {
                screenHeightDp = 480
                screenWidthDp = 360
            }
            CompositionLocalProvider(LocalConfiguration provides compactConfiguration) {
                ChatScreen(viewModel = mockViewModel)
            }
        }

        composeTestRule
            .onNodeWithText("third-event", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("first-event", substring = true)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("second-event", substring = true)
            .assertDoesNotExist()
    }
}
