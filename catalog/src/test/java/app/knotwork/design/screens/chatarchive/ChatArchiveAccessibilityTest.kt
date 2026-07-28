package app.knotwork.design.screens.chatarchive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility audit of the chat-archive surface.
 *
 * The rule for this screen is that **swipe is never the only path**: every
 * action a gesture exposes must also be reachable without one, and state must
 * be carried by glyph + words rather than colour. These tests pin the three
 * ways that can silently regress — a custom action being dropped, the Restore
 * label disappearing when the row collapses to an icon at font-scale 200 %, and
 * a touch target shrinking below the 48 dp floor.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ChatArchiveAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun archiveRow_exposesRestoreAndDeleteWithoutTheSwipeGesture() {
        render { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        val labels = publishedLabels()

        assertTrue("Restore must be reachable without the swipe gesture: $labels", "Restore" in labels)
        assertTrue("Delete forever must be reachable as a custom action: $labels", "Delete forever" in labels)
    }

    @Test
    fun archiveRow_atDefaultScale_announcesRestoreOncePerRow() {
        render { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        // Only each row's swipe strip carries the verb as a description here;
        // the inline pill states it as visible text instead.
        assertEquals(ROW_COUNT, countNodesWithContentDescription("Restore"))
    }

    @Test
    fun archiveRow_keepsRestoreSpokenWhenItCollapsesToAnIcon() {
        render(fontScale = LARGE_FONT_SCALE) { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        // At 200 % the pill collapses to an icon; the word has to survive in the
        // contentDescription or TalkBack announces a bare glyph. One *extra*
        // announcing node per row (strip + collapsed button) is that collapse —
        // and it also proves the compact branch actually ran under test.
        assertEquals(ROW_COUNT * 2, countNodesWithContentDescription("Restore"))
    }

    @Test
    fun swipeStrip_announcesItsVerbNotJustItsColour() {
        render { ChatArchiveContent(state = ChatArchivePreview.swipeOpen()) }

        assertTrue(
            "The revealed action must announce its verb",
            countNodesWithContentDescription("Restore") > 0,
        )
    }

    @Test
    fun emptyState_teachesHowToArchiveAndOffersNoAction() {
        render { ChatArchiveContent(state = ChatArchivePreview.empty()) }

        composeTestRule.onNodeWithText("Nothing archived").assertExists()
        // No CTA on purpose: the only way to archive lives in the chat drawer,
        // so a button here would close the screen the user just opened.
        assertTrue("The empty state must not offer an action", publishedLabels().none { it == "Archive" })
    }

    @Test
    fun rowOverflowButton_meetsTheTouchTargetFloor() {
        render { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        composeTestRule.onAllNodesWithContentDescription("More actions")[0]
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    private fun render(fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            // Inside the theme, which provides `LocalKnotworkA11y` itself — an
            // outer provider would be shadowed and the compact branch would
            // never run, making the 200 % assertions pass for the wrong reason.
            KnotworkTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = fontScale),
                ) {
                    content()
                }
            }
        }
    }

    /**
     * Every label the surface publishes to TalkBack — visible text, content
     * descriptions, and the labels of custom accessibility actions.
     */
    private fun publishedLabels(): Set<String> = composeTestRule.onAllNodes(isRoot().not(), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { node ->
            buildList {
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
                node.config.getOrNull(SemanticsProperties.Text)?.let { texts -> addAll(texts.map { it.text }) }
                node.config.getOrNull(SemanticsActions.CustomActions)?.let { actions ->
                    addAll(actions.map { it.label })
                }
            }
        }
        .toSet()

    private fun countNodesWithContentDescription(value: String): Int =
        composeTestRule.onAllNodesWithContentDescription(value, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size

    private companion object {
        /** Android's minimum interactive size. */
        val MIN_TOUCH_TARGET = 48.dp

        /** The "Largest" system text-size preset. */
        const val LARGE_FONT_SCALE = 2.0f

        /** Rows in the shared populated fixture. */
        val ROW_COUNT = ChatArchivePreview.populated().rows.size
    }
}
