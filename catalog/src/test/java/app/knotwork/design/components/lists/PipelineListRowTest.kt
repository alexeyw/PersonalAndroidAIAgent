package app.knotwork.design.components.lists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.components.chips.Status
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Behavioural tests for [PipelineListRow]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class PipelineListRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping the row body invokes onClick`() {
        var clicks = 0
        composeTestRule.setContent {
            KnotworkTheme {
                PipelineListRow(
                    title = "Daily standup summariser",
                    subtitle = "Run 12 min ago",
                    status = Status.Success,
                    leadingTint = KnotworkTheme.extended.nodeLiteRt,
                    leadingIcon = AppIcons.NodeLite,
                    onClick = { clicks++ },
                    onOverflow = {},
                    onAction = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Daily standup summariser").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `revealed=true exposes Duplicate Archive Delete actions and emits the chosen action`() {
        val emitted = mutableListOf<PipelineSwipeAction>()
        composeTestRule.setContent {
            KnotworkTheme {
                PipelineListRow(
                    title = "Translate inbox",
                    subtitle = "Run failed · 2h ago",
                    status = Status.Error,
                    leadingTint = KnotworkTheme.extended.nodeIntentRouter,
                    leadingIcon = AppIcons.NodeIntentRouter,
                    onClick = {},
                    onOverflow = {},
                    onAction = { emitted += it },
                    revealed = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Duplicate").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Archive").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed().performClick()

        assertEquals(listOf(PipelineSwipeAction.Archive, PipelineSwipeAction.Delete), emitted)
    }
}
