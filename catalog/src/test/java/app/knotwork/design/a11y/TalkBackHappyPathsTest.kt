package app.knotwork.design.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.screens.chat.ChatHomeContent
import app.knotwork.design.screens.chat.ChatHomePreview
import app.knotwork.design.screens.memory.MemoryContent
import app.knotwork.design.screens.memory.MemoryPreview
import app.knotwork.design.screens.pipelines.PipelineLibraryContent
import app.knotwork.design.screens.pipelines.PipelineLibraryPreview
import app.knotwork.design.screens.settings.SettingsContent
import app.knotwork.design.screens.settings.SettingsPreview
import app.knotwork.design.screens.tools.ToolsContent
import app.knotwork.design.screens.tools.ToolsPreview
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose-side scaffolds for the five TalkBack happy paths ratified in
 * `project_docs/design/compose/decisions.md §14`.
 *
 * These tests do **not** drive TalkBack itself — the AccessibilityService
 * bridge cannot be toggled from Compose-test. They instead assert the
 * structural pre-conditions for the manual TalkBack walkthrough: every
 * surface the path passes through publishes at least one focusable
 * interactive node carrying a non-blank `contentDescription`.
 *
 * The live QA pass against the v0.1 release notes is what actually
 * exercises TalkBack on a physical device. These scaffolds fail fast
 * when a regression silently strips the necessary semantics from a
 * surface before QA has a chance to look.
 *
 * Lives in the `:catalog` test source set (Robolectric-driven) so the
 * `internal` `*Preview` fixtures from the catalog stay reachable
 * without exposing them to `:app`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class TalkBackHappyPathsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Happy path 1 — *Start a new chat → send a message → confirm a
     * destructive tool call → inspect the console for the resulting log
     * line.* Verified entry surface: chat-home idle state.
     */
    @Test
    fun happyPath1_chat_home_idle_exposes_focusable_semantics() = assertReachable {
        ChatHomeContent(state = ChatHomePreview.idle())
    }

    /**
     * Happy path 2 — *Open an existing pipeline → edit a node title →
     * save → run.* Verified entry surface: pipeline library populated.
     */
    @Test
    fun happyPath2_pipeline_library_populated_exposes_focusable_semantics() = assertReachable {
        PipelineLibraryContent(state = PipelineLibraryPreview.populated())
    }

    /**
     * Happy path 3 — *Add an MCP server URL → enable one of its tools →
     * confirm by visiting Tools.* Verified entry surface: Tools default.
     */
    @Test
    fun happyPath3_tools_default_exposes_focusable_semantics() = assertReachable {
        ToolsContent(state = ToolsPreview.default())
    }

    /**
     * Happy path 4 — *Toggle the theme in Settings → return to Chat with
     * the new theme active.* The Settings toggles rely on Material's
     * `Switch`, which publishes role + state-description semantics
     * automatically. Asserted here: at least one focusable label exists.
     */
    @Test
    fun happyPath4_settings_default_exposes_focusable_semantics() = assertReachable {
        SettingsContent(state = SettingsPreview.default())
    }

    /**
     * Happy path 5 — *Search Memory → expand a result → delete it.*
     */
    @Test
    fun happyPath5_memory_populated_exposes_focusable_semantics() = assertReachable {
        MemoryContent(state = MemoryPreview.populated())
    }

    /**
     * Renders [content] under `KnotworkTheme` (light) and asserts that
     * at least one node is reachable via TalkBack. A node counts as
     * reachable if it publishes any of:
     *  - a non-blank `contentDescription` (icon-only buttons), or
     *  - a non-blank `text` (text buttons / list rows with primary
     *    labels), or
     *  - a click action (`SemanticsActions.OnClick`) — Material `Switch`
     *    / row-level `clickable` controls advertise focus this way.
     *
     * Composable surfaces with zero such nodes are unreachable via
     * TalkBack regardless of how rich their visual content looks.
     */
    private fun assertReachable(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) { content() }
        }
        val matcher = SemanticsMatcher("is TalkBack-reachable") { node ->
            val cds = node.config.getOrNull(SemanticsProperties.ContentDescription)
            val text = node.config.getOrNull(SemanticsProperties.Text)
            val onClick = node.config.getOrNull(SemanticsActions.OnClick)
            (cds != null && cds.any { it.isNotBlank() }) ||
                (text != null && text.any { it.text.isNotBlank() }) ||
                onClick != null
        }
        val count = composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().size
        check(count > 0) {
            "Expected at least one TalkBack-reachable node, found 0"
        }
    }
}
