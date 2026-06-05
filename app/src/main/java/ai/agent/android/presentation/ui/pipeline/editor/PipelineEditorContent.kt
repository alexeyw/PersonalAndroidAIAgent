package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.presentation.ui.pipeline.editor.bars.MultiSelectToolbar
import ai.agent.android.presentation.ui.pipeline.editor.bars.ValidationBar
import ai.agent.android.presentation.ui.pipeline.editor.canvas.EditorCanvas
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.knotwork.design.components.pipelineeditor.EditorPrimaryAction
import app.knotwork.design.components.pipelineeditor.EditorToolbar
import app.knotwork.design.components.pipelineeditor.NodeError
import app.knotwork.design.components.pipelineeditor.RunStatus
import app.knotwork.design.components.pipelineeditor.RunStatusBanner
import app.knotwork.design.theme.KnotworkTheme

/**
 * Pure-layout content for the [PipelineEditorScreen] — caller provides the live state
 * and lambdas; this composable owns no Hilt / navigation dependencies and so is the
 * deterministic anchor for snapshot tests.
 *
 * Vertical stack: top toolbar (or multi-select bar) → editor canvas → validation bar
 * (or run-trace bar when a run is in progress).
 *
 * The toolbar follows a `[← back] [title +
 * subtitle] [primary action] [overflow]` layout. Undo / Redo / Delete /
 * Auto-layout have moved into the overflow `DropdownMenu` owned by
 * [PipelineEditorScreen] — keeping that lookup table out of this pure-layout
 * composable so the layout stays deterministic for snapshot tests regardless of
 * menu state.
 *
 * @param graph the current pipeline graph from the ViewModel.
 * @param editor the screen-local [EditorState] (gesture, selection, drafts, undo/redo).
 * @param validationErrors validation rule output from `PipelineGraph.validate()`.
 * @param validationLabels per-error human-readable copy (typically resolved from the
 * ViewModel's `labelFor` so wording stays single-sourced with the save-time toast).
 * @param errorsByNodeId map of `nodeId -> NodeError?` for the canvas to render the
 * inline error border / icon on the matching [app.knotwork.design.components.pipelineeditor.NodeCard].
 * @param reducedMotion reduced-motion flag — gates animations longer than `motionSm`.
 * @param toolbarSubtitle subtitle line under the pipeline name — pre-computed by
 * the screen from runState / validation / node count / mini-map state.
 * @param toolbarPrimaryAction `Run` / `Rerun` / `None` — picked by the screen.
 * @param toolbarPrimaryActionEnabled gates the primary action button (e.g. greyed
 * `Run` while validation errors are present).
 * @param runStatus banner status — `Idle` hides the strip; `Running` / `Paused`
 * / `Done` render the matching variant. Banner sits between the toolbar and the
 * canvas so it doesn't fight the validation bar for vertical real estate.
 * @param onRunPause callback for the banner's `Pause` button (Running variant).
 * @param onRunResume callback for the banner's `Resume` button (Paused variant).
 * @param onRunStop callback for the banner's destructive `Stop` button.
 * @param onRunTrace callback for the banner's `Trace` button (Done variant).
 * @param onPipelineNameChange invoked when the inline name field accepts input.
 * @param onNavigateUp invoked when the leading back icon is tapped.
 * @param onPrimaryAction invoked when the primary action button is tapped.
 * @param onOverflow `EditorToolbar` overflow tap — screen opens its own
 * `DropdownMenu` from here.
 * @param onMoveNode forwarded from the canvas drag handler — commits the canvas-space delta.
 * @param onAddNode forwarded from the radial quick-add menu.
 * @param onAddConnection forwarded from a connection-draft drop onto an inbound port.
 * @param onFocusNode forwarded from a `ValidationBar` row tap.
 * @param onMultiSelectCancel exits multi-select without acting.
 * @param onMultiSelectDelete removes every multi-selected node + their connections.
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
    toolbarSubtitle: String?,
    toolbarPrimaryAction: EditorPrimaryAction,
    toolbarPrimaryActionEnabled: Boolean,
    runStatus: RunStatus,
    onRunPause: () -> Unit,
    onRunResume: () -> Unit,
    onRunStop: () -> Unit,
    onRunTrace: () -> Unit,
    onPipelineNameChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOverflow: () -> Unit,
    onMoveNode: (nodeId: String, dxCanvas: Float, dyCanvas: Float) -> Unit,
    onAddNode: (type: NodeType, canvasX: Float, canvasY: Float) -> Unit,
    onAddConnection: (sourceNodeId: String, targetNodeId: String, label: String?) -> Unit,
    onOpenNodeConfig: (nodeId: String) -> Unit,
    onLongPressEdge: (connectionId: String) -> Unit,
    onStartWithInput: () -> Unit,
    onFromTemplate: () -> Unit,
    onFocusNode: (String) -> Unit,
    onAutoFix: () -> Unit,
    onMultiSelectCancel: () -> Unit,
    onMultiSelectCopy: () -> Unit,
    onMultiSelectDelete: () -> Unit,
    activeRunningEdgeIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (editor.multiSelectMode && editor.selection.isNotEmpty()) {
            MultiSelectToolbar(
                count = editor.selection.size,
                onCancel = onMultiSelectCancel,
                onCopy = onMultiSelectCopy,
                onDelete = onMultiSelectDelete,
            )
        } else {
            EditorToolbar(
                name = graph.name,
                onNameChange = onPipelineNameChange,
                onNavigateUp = onNavigateUp,
                onPrimaryAction = onPrimaryAction,
                onOverflow = onOverflow,
                subtitle = toolbarSubtitle,
                primaryAction = toolbarPrimaryAction,
                primaryActionEnabled = toolbarPrimaryActionEnabled,
            )
        }

        // Run status banner sits between the toolbar and the canvas. When status
        // is Idle the composable returns early and consumes zero vertical space —
        // the canvas slides up flush against the toolbar.
        RunStatusBanner(
            status = runStatus,
            onPause = onRunPause,
            onResume = onRunResume,
            onStop = onRunStop,
            onTrace = onRunTrace,
            modifier = Modifier.padding(
                horizontal = KnotworkTheme.spacing.sp3,
                vertical = KnotworkTheme.spacing.sp2,
            ),
        )

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
            onStartWithInput = onStartWithInput,
            onFromTemplate = onFromTemplate,
            modifier = Modifier.weight(1f),
        )

        // ValidationBar at the bottom. During a live run the [RunStatusBanner]
        // at the top owns the run-progress messaging; the bottom bar stays put
        // and reports validation state so the user still sees the gate.
        ValidationBar(
            graph = graph,
            errors = validationErrors,
            errorLabels = validationLabels,
            nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
            onFocusNode = onFocusNode,
            onAutoFix = onAutoFix,
        )
    }
}
