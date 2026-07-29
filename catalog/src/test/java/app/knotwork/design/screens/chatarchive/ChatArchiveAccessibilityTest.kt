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
 *
 * On that last one, note which property is being measured.
 * `assertWidthIsAtLeast` reports a node's **visual** box. Compose expands a
 * control's hit area *past* that box — Material components and Foundation's
 * `clickable` both — so a visual assertion says nothing about reach. The two
 * are asserted separately here: the visual ones pin sizes the design asks for,
 * and `touchBoundsInRoot` is the one that speaks for a finger.
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
    fun archiveRow_announcesRestoreOncePerRow() {
        render { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        // Exactly one per row — the swipe strip. There is no inline Restore
        // button any more: on a 360 dp row it ate the title and stood as a
        // third Restore beside the strip and the overflow item.
        assertEquals(ROW_COUNT, countNodesWithContentDescription("Restore"))
    }

    @Test
    fun archiveRow_atLargeFontStillAnnouncesRestoreOncePerRow() {
        render(fontScale = LARGE_FONT_SCALE) { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        // Unchanged at 200 %: the row sheds its leading tile, not an action.
        assertEquals(ROW_COUNT, countNodesWithContentDescription("Restore"))
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
    fun revealedSwipeAction_fillsTheRow() {
        render { ChatArchiveContent(state = ChatArchivePreview.swipeOpen()) }

        // Regression: the strip used `fillMaxHeight` inside a wrap-content Box,
        // which inside a LazyColumn has unbounded height — so it silently
        // collapsed to a ~32 dp band floating beside the row instead of the
        // full-height strip the design draws.
        //
        // A visual assertion, then. Reach was never the problem: Compose
        // expands a `clickable`'s touch bounds to 48 dp regardless, which
        // `everyRowControlIsReachableByTouch` below is what actually checks.
        composeTestRule.onAllNodesWithContentDescription("Restore")[0]
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun rowOverflowButton_matchesTheDesignsVisualSize() {
        render { ChatArchiveContent(state = ChatArchivePreview.populated()) }

        // A *visual* assertion, deliberately. The handoff specifies "⋮ 48", and
        // this pins that. It is not an accessibility guard: Material expands an
        // `IconButton`'s touch bounds to 48 dp whatever it is laid out at, so
        // this control was never hard to hit — see the touch-bounds check below.
        composeTestRule.onAllNodesWithContentDescription("More actions")[0]
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun everyRowControlIsReachableByTouch() {
        render { ChatArchiveContent(state = ChatArchivePreview.swipeOpen()) }

        // The assertion that actually speaks for a user's finger. `node.size`
        // (what `assertWidthIsAtLeast` measures) is the *visual* box, and
        // Compose grows the hit area past it — for Material components and for
        // Foundation's `clickable` alike. Only `touchBoundsInRoot` sees that,
        // so it is the only one of the two that can claim to test reach.
        listOf("Restore", "More actions").forEach(::assertTouchBoundsMeetTheFloor)
    }

    /**
     * Asserts every node carrying [contentDescription] presents at least
     * [MIN_TOUCH_TARGET] of **touch** area in both axes.
     */
    private fun assertTouchBoundsMeetTheFloor(contentDescription: String) {
        val floorPx = with(composeTestRule.density) { MIN_TOUCH_TARGET.toPx() }
        // Merged tree on purpose: unmerged resolves a description to the `Icon`
        // that carries it, which is the 24 dp glyph rather than the control the
        // finger aims at.
        val nodes = composeTestRule
            .onAllNodesWithContentDescription(contentDescription)
            .fetchSemanticsNodes()
        assertTrue("Expected a \"$contentDescription\" control to measure", nodes.isNotEmpty())
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue(
                "\"$contentDescription\" touch area is ${bounds.width}x${bounds.height} px, " +
                    "below the ${MIN_TOUCH_TARGET.value.toInt()} dp floor",
                bounds.width >= floorPx && bounds.height >= floorPx,
            )
        }
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
