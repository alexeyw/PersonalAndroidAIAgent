package app.knotwork.design.components.pipelineeditor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Pins the editor toolbar's action roster.
 *
 * The toolbar used to carry a primary `Run` button that did not run anything:
 * it saved the graph, flipped a local `isRunning` flag to animate a banner,
 * and raised a snackbar admitting the execution engine was not wired. Runs
 * belong to chat, where the console reports them, so the editor composes
 * pipelines and does not pretend to execute them.
 *
 * These tests fail if a run affordance returns to the toolbar, and cover the
 * surviving actions so the removal cannot take a working one with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class EditorToolbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun render(onNavigateUp: () -> Unit = {}, onOverflow: () -> Unit = {}) {
        composeTestRule.setContent {
            KnotworkTheme {
                EditorToolbar(
                    name = "Weekly digest",
                    onNameChange = {},
                    onNavigateUp = onNavigateUp,
                    onOverflow = onOverflow,
                    subtitle = "Editing · 4 nodes · 3 edges",
                )
            }
        }
    }

    @Test
    fun `toolbar offers no run affordance`() {
        render()

        composeTestRule.onNodeWithText("Run").assertDoesNotExist()
        composeTestRule.onNodeWithText("Re-run").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pause").assertDoesNotExist()
        composeTestRule.onNodeWithText("Stop").assertDoesNotExist()
    }

    @Test
    fun `toolbar renders the name and subtitle`() {
        render()

        composeTestRule.onNodeWithText("Weekly digest").assertExists()
        composeTestRule.onNodeWithText("Editing · 4 nodes · 3 edges").assertExists()
    }

    @Test
    fun `back and overflow dispatch their callbacks`() {
        var back = 0
        var overflow = 0
        render(onNavigateUp = { back++ }, onOverflow = { overflow++ })

        composeTestRule
            .onNodeWithContentDescription(string(R.string.knotwork_editor_action_navigate_up))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.knotwork_editor_action_overflow))
            .performClick()

        assertEquals(1, back)
        assertEquals(1, overflow)
    }
}
