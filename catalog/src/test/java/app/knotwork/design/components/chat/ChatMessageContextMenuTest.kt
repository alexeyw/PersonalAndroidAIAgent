package app.knotwork.design.components.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.R
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the roster of the long-press message context menu.
 *
 * "Rate" used to sit between Re-run and Save to memory, and its only effect
 * was a snackbar reading "Message rating is not yet available." — a menu row
 * whose whole behaviour was an apology. It is removed rather than disabled,
 * per `docs/decisions/0005-a-control-that-cannot-act-is-removed.md`. This
 * test fails if it comes back, and covers the surviving rows so the removal
 * cannot take a working one with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ChatMessageContextMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private val bubbleText = "Yesterday's deploy went out at 18:40."

    private fun openMenu(role: ChatRole = ChatRole.Assistant): MutableList<ChatContextAction> {
        val actions = mutableListOf<ChatContextAction>()
        composeTestRule.setContent {
            KnotworkTheme {
                ChatMessage(
                    role = role,
                    content = ChatContent.Text(text = bubbleText),
                    metadata = ChatMetadata(timestamp = "09:14"),
                    onContextAction = { actions += it },
                )
            }
        }
        composeTestRule.onNodeWithText(bubbleText).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        return actions
    }

    @Test
    fun `context menu does not offer Rate`() {
        openMenu()

        composeTestRule.onNodeWithText("Rate").assertDoesNotExist()
    }

    @Test
    fun `context menu keeps copy, re-run and save-to-memory`() {
        openMenu()

        composeTestRule
            .onNodeWithText(string(R.string.knotwork_chat_message_action_copy))
            .assertExists()
        composeTestRule
            .onNodeWithText(string(R.string.knotwork_chat_message_action_rerun))
            .assertExists()
        composeTestRule
            .onNodeWithText(string(R.string.knotwork_chat_message_action_save_to_memory))
            .assertExists()
    }

    @Test
    fun `tapping a surviving row dispatches its action`() {
        val actions = openMenu()

        composeTestRule
            .onNodeWithText(string(R.string.knotwork_chat_message_action_save_to_memory))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(ChatContextAction.SaveToMemory), actions)
    }
}
