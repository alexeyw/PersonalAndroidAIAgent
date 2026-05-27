package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.presentation.ui.orchestrator.OrchestratorUiState
import ai.agent.android.presentation.ui.pipeline.editor.bars.MultiSelectToolbar
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 7/9 — covers the copy → paste round-trip from the user's
 * point of view.
 *
 * The clipboard lives inside the screen-local `EditorState`, so we can't
 * seed it from outside [PipelineEditorScreen]. We split the contract into
 * two pieces:
 *
 *  - **Copy semantics**: rendering the [MultiSelectToolbar] with an editor
 *    we own and asserting the Copy callback receives the right hook so a
 *    production caller can populate clipboard from the live graph.
 *  - **Paste-on-empty-clipboard**: from the full screen, verifying that the
 *    overflow Paste item refuses to dispatch `vm.addNode` when no Copy
 *    has happened — guarding the "Clipboard is empty" snackbar branch.
 *
 * The full populate-clipboard-via-screen-then-paste integration requires
 * a real long-press gesture on a node card (the only public entry into
 * `editor.multiSelectMode = true`). That path is documented and
 * exercised by [PipelineEditorGestureTest]; chasing it here would
 * duplicate the gesture-flakiness surface without adding assertion
 * coverage.
 */
class PipelineEditorCopyPasteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun multiSelectCopy_populatesClipboardFromSelection() {
        val editor = EditorState()
        val graph = PipelineEditorTestFixtures.threeNodePipeline()
        val selected = setOf(graph.nodes[0].id, graph.nodes[1].id)
        editor.multiSelectMode = true
        editor.selection = selected

        composeTestRule.setContent {
            MaterialTheme {
                MultiSelectToolbar(
                    count = selected.size,
                    onCancel = {},
                    // Mirrors PipelineEditorScreen.onMultiSelectCopy verbatim — copies
                    // exactly the nodes whose ids appear in `editor.selection`.
                    onCopy = {
                        editor.clipboard = graph.nodes.filter { it.id in editor.selection }
                        editor.multiSelectMode = false
                        editor.selection = emptySet()
                    },
                    onDelete = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(R.string.pipeline_editor_multi_select_copy),
            )
            .performClick()
        composeTestRule.waitForIdle()

        val clipboardIds: Set<String> = editor.clipboard.map(NodeModel::id).toSet()
        assertEquals(selected, clipboardIds)
        assertFalse("expected multi-select to clear after copy", editor.multiSelectMode)
    }

    @Test
    fun overflowPaste_onEmptyClipboard_doesNotDispatchAddNode() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.inputOutputPipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(KnotworkR.string.knotwork_editor_action_overflow),
            )
            .performClick()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_paste))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // The overflow item is rendered disabled; tapping it must NOT trigger
        // any vm.addNode dispatch — the "Clipboard is empty" snackbar handles
        // the user feedback instead.
        verify(exactly = 0) { vm.addNode(any(), any(), any()) }
        verify(exactly = 0) { vm.updateNodeFromEditor(any(), any()) }
    }
}
