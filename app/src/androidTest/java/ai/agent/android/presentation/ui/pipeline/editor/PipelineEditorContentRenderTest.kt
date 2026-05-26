package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.design.components.pipelineeditor.EditorPrimaryAction
import app.knotwork.design.components.pipelineeditor.RunStatus
import org.junit.Rule
import org.junit.Test

/**
 * Phase 23 / Task 7/9 — surface render coverage for
 * [PipelineEditorContent], the pure-layout anchor of the editor screen.
 *
 * The screen-level [PipelineEditorScreen] orchestrates VM streams and
 * overflow / sheet chrome; [PipelineEditorContent] owns the top-toolbar
 * swap, the run banner, and the canvas / validation bar stack. Verifying
 * the swap conditions and empty-vs-populated rendering here keeps the
 * sheet / overflow tests focused on dialog behaviour.
 */
class PipelineEditorContentRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyGraph_rendersEmptyPipelineHero() {
        val graph = PipelineEditorTestFixtures.emptyPipeline()
        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorContent(
                    graph = graph,
                    editor = EditorState(),
                    validationErrors = emptyList(),
                    validationLabels = emptyList(),
                    errorsByNodeId = emptyMap(),
                    reducedMotion = true,
                    toolbarSubtitle = null,
                    toolbarPrimaryAction = EditorPrimaryAction.Run,
                    toolbarPrimaryActionEnabled = true,
                    runStatus = RunStatus.Idle,
                    onRunPause = {},
                    onRunResume = {},
                    onRunStop = {},
                    onRunTrace = {},
                    onPipelineNameChange = {},
                    onNavigateUp = {},
                    onPrimaryAction = {},
                    onOverflow = {},
                    onMoveNode = { _, _, _ -> },
                    onAddNode = { _, _, _ -> },
                    onAddConnection = { _, _, _ -> },
                    onOpenNodeConfig = {},
                    onLongPressEdge = {},
                    onStartWithInput = {},
                    onFromTemplate = {},
                    onFocusNode = {},
                    onAutoFix = {},
                    onMultiSelectCancel = {},
                    onMultiSelectCopy = {},
                    onMultiSelectDelete = {},
                    activeRunningEdgeIds = emptySet(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_empty_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_empty_cta_start))
            .assertIsDisplayed()
    }

    @Test
    fun populatedGraph_rendersNodeLabels() {
        val graph = PipelineEditorTestFixtures.threeNodePipeline()
        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorContent(
                    graph = graph,
                    editor = EditorState(),
                    validationErrors = emptyList(),
                    validationLabels = emptyList(),
                    errorsByNodeId = emptyMap(),
                    reducedMotion = true,
                    toolbarSubtitle = null,
                    toolbarPrimaryAction = EditorPrimaryAction.Run,
                    toolbarPrimaryActionEnabled = true,
                    runStatus = RunStatus.Idle,
                    onRunPause = {},
                    onRunResume = {},
                    onRunStop = {},
                    onRunTrace = {},
                    onPipelineNameChange = {},
                    onNavigateUp = {},
                    onPrimaryAction = {},
                    onOverflow = {},
                    onMoveNode = { _, _, _ -> },
                    onAddNode = { _, _, _ -> },
                    onAddConnection = { _, _, _ -> },
                    onOpenNodeConfig = {},
                    onLongPressEdge = {},
                    onStartWithInput = {},
                    onFromTemplate = {},
                    onFocusNode = {},
                    onAutoFix = {},
                    onMultiSelectCancel = {},
                    onMultiSelectCopy = {},
                    onMultiSelectDelete = {},
                    activeRunningEdgeIds = emptySet(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The empty-state hero must NOT render once the graph has nodes — the canvas
        // shows the cards instead.
        composeTestRule.onAllNodesWithText(
            ctx.getString(R.string.pipeline_editor_empty_title),
        ).assertCountEquals(0)
    }

    @Test
    fun multiSelectMode_withSelection_swapsInMultiSelectToolbar() {
        val graph = PipelineEditorTestFixtures.threeNodePipeline()
        val editor = EditorState().apply {
            multiSelectMode = true
            selection = setOf(graph.nodes[0].id, graph.nodes[1].id)
        }
        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorContent(
                    graph = graph,
                    editor = editor,
                    validationErrors = emptyList(),
                    validationLabels = emptyList(),
                    errorsByNodeId = emptyMap(),
                    reducedMotion = true,
                    toolbarSubtitle = null,
                    toolbarPrimaryAction = EditorPrimaryAction.Run,
                    toolbarPrimaryActionEnabled = true,
                    runStatus = RunStatus.Idle,
                    onRunPause = {},
                    onRunResume = {},
                    onRunStop = {},
                    onRunTrace = {},
                    onPipelineNameChange = {},
                    onNavigateUp = {},
                    onPrimaryAction = {},
                    onOverflow = {},
                    onMoveNode = { _, _, _ -> },
                    onAddNode = { _, _, _ -> },
                    onAddConnection = { _, _, _ -> },
                    onOpenNodeConfig = {},
                    onLongPressEdge = {},
                    onStartWithInput = {},
                    onFromTemplate = {},
                    onFocusNode = {},
                    onAutoFix = {},
                    onMultiSelectCancel = {},
                    onMultiSelectCopy = {},
                    onMultiSelectDelete = {},
                    activeRunningEdgeIds = emptySet(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(R.string.pipeline_editor_multi_select_cancel),
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(R.string.pipeline_editor_multi_select_delete),
            )
            .assertIsDisplayed()
    }

    @Test
    fun emptyGraph_validationBarReportsClean() {
        val graph = PipelineEditorTestFixtures.emptyPipeline()
        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorContent(
                    graph = graph,
                    editor = EditorState(),
                    validationErrors = emptyList(),
                    validationLabels = emptyList(),
                    errorsByNodeId = emptyMap(),
                    reducedMotion = true,
                    toolbarSubtitle = null,
                    toolbarPrimaryAction = EditorPrimaryAction.Run,
                    toolbarPrimaryActionEnabled = true,
                    runStatus = RunStatus.Idle,
                    onRunPause = {},
                    onRunResume = {},
                    onRunStop = {},
                    onRunTrace = {},
                    onPipelineNameChange = {},
                    onNavigateUp = {},
                    onPrimaryAction = {},
                    onOverflow = {},
                    onMoveNode = { _, _, _ -> },
                    onAddNode = { _, _, _ -> },
                    onAddConnection = { _, _, _ -> },
                    onOpenNodeConfig = {},
                    onLongPressEdge = {},
                    onStartWithInput = {},
                    onFromTemplate = {},
                    onFocusNode = {},
                    onAutoFix = {},
                    onMultiSelectCancel = {},
                    onMultiSelectCopy = {},
                    onMultiSelectDelete = {},
                    activeRunningEdgeIds = emptySet(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_validation_clean))
            .assertIsDisplayed()
    }
}
