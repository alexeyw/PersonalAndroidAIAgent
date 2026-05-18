package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.presentation.ui.pipeline.editor.bars.MultiSelectToolbar
import ai.agent.android.presentation.ui.pipeline.editor.bars.RunTraceBar
import ai.agent.android.presentation.ui.pipeline.editor.bars.ValidationBar
import ai.agent.android.presentation.ui.pipeline.editor.canvas.EditorCanvas
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.knotwork.design.components.pipelineeditor.EditorToolbar
import app.knotwork.design.components.pipelineeditor.NodeError

/**
 * Pure-layout content for the [PipelineEditorScreen] — caller provides the live state
 * and lambdas; this composable owns no Hilt / navigation dependencies and so is the
 * deterministic anchor for snapshot tests.
 *
 * Vertical stack: top toolbar (or multi-select bar) → editor canvas → validation bar
 * (or run-trace bar when a run is in progress).
 *
 * @param graph the current pipeline graph from the ViewModel.
 * @param editor the screen-local [EditorState] (gesture, selection, drafts, undo/redo).
 * @param validationErrors validation rule output from `PipelineGraph.validate()`.
 * @param validationLabels per-error human-readable copy (typically resolved from the
 * ViewModel's `labelFor` so wording stays single-sourced with the save-time toast).
 * @param errorsByNodeId map of `nodeId -> NodeError?` for the canvas to render the
 * inline error border / icon on the matching [app.knotwork.design.components.pipelineeditor.NodeCard].
 * @param reducedMotion reduced-motion flag — gates animations longer than `motionSm`.
 * @param onPipelineNameChange invoked when the inline name field accepts input.
 * @param onUndo `EditorToolbar` action — proxied to the undo stack.
 * @param onRedo `EditorToolbar` action — proxied to the redo stack.
 * @param onDeleteSelection `EditorToolbar` action — removes selected nodes.
 * @param onAutoLayout `EditorToolbar` action — re-runs Sugiyama layout.
 * @param onRun `EditorToolbar` action — flips the run-trace bar on.
 * @param onOverflow `EditorToolbar` overflow tap.
 * @param onMoveNode forwarded from the canvas drag handler — commits the canvas-space delta.
 * @param onAddNode forwarded from the radial quick-add menu.
 * @param onAddConnection forwarded from a connection-draft drop onto an inbound port.
 * @param onFocusNode forwarded from a `ValidationBar` row tap.
 * @param onMultiSelectCancel exits multi-select without acting.
 * @param onMultiSelectDelete removes every multi-selected node + their connections.
 * @param activeRunningNodeLabel display label of the running node when in run mode.
 * @param activeRunningEdgeIds edge ids the run-trace dot animation should follow.
 */
@Composable
@Suppress("LongParameterList")
internal fun PipelineEditorContent(
    graph: PipelineGraph,
    editor: EditorState,
    validationErrors: List<PipelineValidationError>,
    validationLabels: List<String>,
    errorsByNodeId: Map<String, NodeError?>,
    reducedMotion: Boolean,
    onPipelineNameChange: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDeleteSelection: () -> Unit,
    onAutoLayout: () -> Unit,
    onRun: () -> Unit,
    onOverflow: () -> Unit,
    onMoveNode: (nodeId: String, dxCanvas: Float, dyCanvas: Float) -> Unit,
    onAddNode: (type: NodeType, canvasX: Float, canvasY: Float) -> Unit,
    onAddConnection: (sourceNodeId: String, targetNodeId: String, label: String?) -> Unit,
    onOpenNodeConfig: (nodeId: String) -> Unit,
    onLongPressEdge: (connectionId: String) -> Unit,
    onFocusNode: (String) -> Unit,
    onMultiSelectCancel: () -> Unit,
    onMultiSelectDelete: () -> Unit,
    activeRunningNodeLabel: String?,
    activeRunningEdgeIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (editor.multiSelectMode && editor.selection.isNotEmpty()) {
            MultiSelectToolbar(
                count = editor.selection.size,
                onCancel = onMultiSelectCancel,
                onDelete = onMultiSelectDelete,
            )
        } else {
            EditorToolbar(
                name = graph.name,
                onNameChange = onPipelineNameChange,
                onUndo = onUndo,
                onRedo = onRedo,
                onDelete = onDeleteSelection,
                onAutoLayout = onAutoLayout,
                onRun = onRun,
                onOverflow = onOverflow,
                undoEnabled = editor.undoRedo.canUndo,
                redoEnabled = editor.undoRedo.canRedo,
                // Delete is now enabled for either a node selection OR a selected edge —
                // tap an edge → press the Delete icon → connection removed.
                deleteEnabled = editor.selection.isNotEmpty() || editor.selectedEdgeId != null,
                runEnabled = validationErrors.isEmpty(),
            )
        }

        EditorCanvas(
            graph = graph,
            editor = editor,
            activeRunningEdgeIds = activeRunningEdgeIds,
            errorsByNodeId = errorsByNodeId,
            reducedMotion = reducedMotion,
            onMoveNode = onMoveNode,
            onAddNode = onAddNode,
            onAddConnection = onAddConnection,
            onOpenNodeConfig = onOpenNodeConfig,
            onLongPressEdge = onLongPressEdge,
            modifier = Modifier.weight(1f),
        )

        if (editor.isRunning) {
            RunTraceBar(activeNodeLabel = activeRunningNodeLabel)
        } else {
            ValidationBar(
                errors = validationErrors,
                errorLabels = validationLabels,
                nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
                onFocusNode = onFocusNode,
            )
        }
    }
}
