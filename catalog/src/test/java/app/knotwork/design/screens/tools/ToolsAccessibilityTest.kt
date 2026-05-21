package app.knotwork.design.screens.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tools-screen a11y audit (Phase 22 / Task 11).
 *
 * Tighter follow-up to the structural reachability test in
 * [app.knotwork.design.a11y.TalkBackHappyPathsTest], which only asserts that
 * the default Tools surface exposes at least one focusable node. This suite
 * walks happy path #3 (`decisions.md §14` — "Add an MCP server URL → enable
 * one of its tools → confirm by visiting Tools") and asserts a minimum
 * count of TalkBack-reachable nodes on each surface plus the explicit
 * `Risk level:` announcement on every risk pill so colour is never the
 * only signal.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ToolsAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toolsDefault_builtInAndMcpRowsAreReachable() {
        render { ToolsContent(state = ToolsPreview.default()) }
        // 3 built-in rows × (row trigger + per-row Switch + per-row overflow)
        // is over-spec; we floor on the structural minimum: 3 built-in
        // triggers + 3 server triggers + 3 server overflow buttons = 9.
        assertReachableNodes(min = DEFAULT_MIN_NODES)
        assertContentDescriptionPresent(substring = "Risk level: Read only")
        assertContentDescriptionPresent(substring = "Risk level: Sensitive")
        assertContentDescriptionPresent(substring = "Risk level: Destructive")
    }

    @Test
    fun toolsDefaultExpanded_nestedToolsAreReachable() {
        render { ToolsContent(state = ToolsPreview.defaultExpanded()) }
        // Expanded server adds 2 nested tool triggers (`nestedTools()` in
        // `ToolsPreview`) on top of the default floor.
        assertReachableNodes(min = DEFAULT_MIN_NODES + EXPANDED_MIN_EXTRA_NODES)
    }

    @Test
    fun toolDetailDefault_schemaToggleAndBackAreReachable() {
        render { ToolDetailContent(state = ToolsPreview.toolDetailDefault()) }
        // Back button + enable Switch is the structural minimum; the
        // description / schema-body text adds further reachable text
        // nodes — the floor of 2 catches the "icon-only Back leaks past
        // the matcher" regression class.
        assertReachableNodes(min = TOOL_DETAIL_MIN_NODES)
    }

    @Test
    fun toolDetailSchemaError_inlineTileIsReachable() {
        render { ToolDetailContent(state = ToolsPreview.toolDetailSchemaError()) }
        // Back button is still there; the disabled Switch still emits an
        // `OnClick` action, so the floor stays at 2 — the error tile body
        // is plain text, also reachable.
        assertReachableNodes(min = TOOL_DETAIL_MIN_NODES)
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

    private fun assertContentDescriptionPresent(substring: String) {
        val matcher = SemanticsMatcher("has CD containing '$substring'") { node ->
            val cds = node.config.getOrNull(SemanticsProperties.ContentDescription) ?: return@SemanticsMatcher false
            cds.any { it.contains(substring, ignoreCase = true) }
        }
        val matches = composeTestRule.onAllNodes(matcher).fetchSemanticsNodes()
        check(matches.isNotEmpty()) {
            "Expected at least one node whose contentDescription contains '$substring'"
        }
    }
}

/**
 * Minimum reachable nodes on the default Tools surface (3 built-in row
 * triggers + 3 server row triggers + 3 server overflow buttons).
 */
private const val DEFAULT_MIN_NODES = 9

/** Extra nodes the expanded server adds (2 nested tool-entry triggers). */
private const val EXPANDED_MIN_EXTRA_NODES = 2

/** Minimum reachable nodes on the ToolDetail screen (Back + enable Switch). */
private const val TOOL_DETAIL_MIN_NODES = 2
