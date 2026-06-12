package app.knotwork.design.components.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioural coverage for [InterruptedRunCard] — the status card the chat
 * stream pins when the session's most recent pipeline run died with its
 * process. Verifies the rendered copy (header + node label) and that the
 * Resume / Discard CTAs dispatch their callbacks exactly once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class InterruptedRunCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersHeaderAndNodeLabel() {
        composeTestRule.setContent {
            KnotworkTheme {
                InterruptedRunCard(
                    model = InterruptedRunCardModel(nodeLabel = "Summarise"),
                    onResume = {},
                    onDiscard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Run interrupted").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Execution was interrupted at node “Summarise”.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()
    }

    @Test
    fun resumeCtaDispatchesCallback() {
        var resumed = 0
        var discarded = 0
        composeTestRule.setContent {
            KnotworkTheme {
                InterruptedRunCard(
                    model = InterruptedRunCardModel(nodeLabel = "Summarise"),
                    onResume = { resumed++ },
                    onDiscard = { discarded++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Resume").performClick()

        assertEquals(1, resumed)
        assertEquals(0, discarded)
    }

    @Test
    fun discardCtaDispatchesCallback() {
        var resumed = false
        var discarded = false
        composeTestRule.setContent {
            KnotworkTheme {
                InterruptedRunCard(
                    model = InterruptedRunCardModel(nodeLabel = "Summarise"),
                    onResume = { resumed = true },
                    onDiscard = { discarded = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Discard").performClick()

        assertTrue(discarded)
        assertFalse(resumed)
    }

    @Test
    fun nonResumableVariantHidesResumeAndShowsExpiredNote() {
        composeTestRule.setContent {
            KnotworkTheme {
                InterruptedRunCard(
                    model = InterruptedRunCardModel(nodeLabel = "Summarise", resumable = false),
                    onResume = {},
                    onDiscard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Resume").assertDoesNotExist()
        composeTestRule.onNodeWithText("This run can no longer be resumed.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()
    }
}
