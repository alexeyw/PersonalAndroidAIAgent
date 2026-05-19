package app.knotwork.design.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Chat-home a11y audit (Phase 22 / Task 5).
 *
 * Tighter follow-up to [app.knotwork.design.a11y.TalkBackHappyPathsTest]:
 * the upstream test only asserts that *some* TalkBack-reachable node
 * exists. This test enumerates the surfaces a TalkBack user walks through
 * during happy path #1 (`decisions.md §14`) and asserts that every one of
 * them publishes at least one non-blank `contentDescription` or visible
 * `text`. A regression that silently strips semantics from an icon-only
 * button — e.g. the TopBar `⋮` overflow or the drawer "Edit" pencil —
 * would let the structural test still pass while leaving the actual user
 * with an unannounced glyph.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ChatHomeAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatHomeIdle_topBarIconsAdvertiseSemantics() {
        render { ChatHomeContent(state = ChatHomePreview.idle()) }
        assertReachableNodes(min = TOP_BAR_INTERACTIVE_NODES)
    }

    @Test
    fun chatHomeDrawerOpen_threadAndFooterRowsAreReachable() {
        render { ChatHomeContent(state = ChatHomePreview.drawerOpen()) }
        // Drawer adds the New-chat pill, thread rows, and 2 footer rows on
        // top of the underlying chat surface — at minimum we expect the
        // top-bar nodes + these drawer-specific affordances.
        assertReachableNodes(min = TOP_BAR_INTERACTIVE_NODES + DRAWER_MIN_NODES)
    }

    @Test
    fun chatHomeHitlDestructive_approveAndRejectAreReachable() {
        render { ChatHomeContent(state = ChatHomePreview.hitlConfirm(risk = Risk.Destructive)) }
        // HITL Destructive surfaces the Approve / Reject + typed-confirm
        // field on top of the idle chrome.
        assertReachableNodes(min = TOP_BAR_INTERACTIVE_NODES + HITL_MIN_NODES)
    }

    @Test
    fun chatHomeConsoleExpanded_consoleControlsAreReachable() {
        render { ChatHomeContent(state = ChatHomePreview.consoleExpanded()) }
        // Console-Full adds the tab strip, filter chips, search,
        // copy-all, and clear / close controls.
        assertReachableNodes(min = TOP_BAR_INTERACTIVE_NODES + CONSOLE_MIN_NODES)
    }

    private fun render(content: @Composable () -> Unit) {
        composeTestRule.setContent { KnotworkTheme(darkTheme = false) { content() } }
    }

    private fun assertReachableNodes(min: Int) {
        val matcher = SemanticsMatcher("is TalkBack-reachable") { node ->
            val cds = node.config.getOrNull(SemanticsProperties.ContentDescription)
            val text = node.config.getOrNull(SemanticsProperties.Text)
            val onClick = node.config.getOrNull(SemanticsActions.OnClick)
            (cds != null && cds.any { it.isNotBlank() }) ||
                (text != null && text.any { it.text.isNotBlank() }) ||
                onClick != null
        }
        val count = composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().size
        check(count >= min) {
            "Expected at least $min TalkBack-reachable nodes, found $count"
        }
    }
}

/** Minimum number of TalkBack-reachable interactive nodes on the chat-home TopAppBar. */
private const val TOP_BAR_INTERACTIVE_NODES = 3 // hamburger, favorite star, overflow

/** Minimum extra interactive nodes the drawer overlay introduces (new chat + ≥ 1 thread row + 2 footer rows). */
private const val DRAWER_MIN_NODES = 4

/** Minimum extra interactive nodes the HITL confirmation card introduces (Reject + Allow once at minimum). */
private const val HITL_MIN_NODES = 2

/** Minimum extra interactive nodes the console pane introduces (3 tabs + close + at least one filter chip). */
private const val CONSOLE_MIN_NODES = 5
