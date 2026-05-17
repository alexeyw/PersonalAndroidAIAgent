package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.resolve
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeConfigCodec
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import ai.agent.android.presentation.ui.pipeline.editor.core.AutoLayout
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import ai.agent.android.presentation.ui.pipeline.editor.core.rememberEditorState
import ai.agent.android.presentation.ui.pipeline.editor.sheet.NodeConfigSheetHost
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.knotwork.design.theme.KnotworkTheme
import kotlinx.coroutines.launch

/**
 * Phase-21 production pipeline editor — the Knotwork-redesigned canvas surface.
 *
 * Replaces the legacy `VisualOrchestratorScreen`. Shares the `OrchestratorViewModel`
 * scoped to the `pipelines` nested nav-graph with the library screen so a freshly
 * created / loaded pipeline appears here without an extra read.
 *
 * Responsibilities:
 *  - Subscribes to the VM's `uiState` + `runState` + `focusNodeRequest` streams.
 *  - Owns the screen-local [EditorState] (selection / undo / drafts).
 *  - Hosts the [PipelineEditorContent] layout and the catalog `NodeConfigSheet`.
 *  - Dispatches graph mutations back to the VM (which persists through `SavePipelineUseCase`).
 *
 * @param viewModel the shared orchestrator view model (graph-scoped).
 * @param onBack invoked when the user navigates back to the library. The editor surfaces
 * the back affordance via `BackHandler` — system back closes any open sheet or
 * multi-select session first, then falls through to this lambda.
 */
@Composable
@Suppress("LongMethod") // The editor screen is the orchestration seam; splitting would hide the data flow.
fun PipelineEditorScreen(viewModel: OrchestratorViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val runState by viewModel.runState.collectAsState()
    val editor: EditorState = rememberEditorState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(runState) {
        editor.isRunning = runState.isRunning
        editor.activeRunningNodeId = runState.activeNodeId
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = context.resolve(msg))
        viewModel.clearError()
    }
    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = context.resolve(msg))
        viewModel.clearFeedback()
    }
    LaunchedEffect(Unit) {
        viewModel.focusNodeRequest.collect { nodeId ->
            val target = uiState.currentPipeline.nodes.find { it.id == nodeId } ?: return@collect
            editor.selection = setOf(nodeId)
            editor.multiSelectMode = false
            editor.transform = editor.transform.centeredOn(
                x = target.x,
                y = target.y,
                viewportW = 1f,
                viewportH = 1f,
            )
        }
    }

    BackHandler {
        when {
            editor.configuringNodeId != null -> {
                editor.configuringNodeId = null
                editor.workingConfig = null
            }
            editor.multiSelectMode -> {
                editor.multiSelectMode = false
                editor.selection = emptySet()
            }
            editor.quickAddAnchor != null -> {
                editor.quickAddAnchor = null
            }
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val validationErrors = uiState.validationErrors
        val validationLabels = remember(validationErrors) {
            validationErrors.map { viewModel.labelFor(it) }
        }
        PipelineEditorContent(
            graph = uiState.currentPipeline,
            editor = editor,
            validationErrors = validationErrors,
            validationLabels = validationLabels.map { context.resolve(it) },
            errorsByNodeId = emptyMap(),
            reducedMotion = KnotworkTheme.a11y.reducedMotion(),
            onPipelineNameChange = { name ->
                viewModel.replaceCurrentPipeline(uiState.currentPipeline.copy(name = name))
            },
            onUndo = {
                val previous = editor.undoRedo.undo(uiState.currentPipeline) ?: return@PipelineEditorContent
                viewModel.replaceCurrentPipeline(previous)
            },
            onRedo = {
                val next = editor.undoRedo.redo(uiState.currentPipeline) ?: return@PipelineEditorContent
                viewModel.replaceCurrentPipeline(next)
            },
            onDeleteSelection = {
                if (editor.selection.isEmpty()) return@PipelineEditorContent
                editor.undoRedo.push(uiState.currentPipeline)
                editor.selection.forEach { id -> viewModel.removeNode(id) }
                editor.selection = emptySet()
                editor.multiSelectMode = false
            },
            onAutoLayout = {
                editor.undoRedo.push(uiState.currentPipeline)
                val result = AutoLayout.compute(uiState.currentPipeline)
                val nextNodes = uiState.currentPipeline.nodes.map { node ->
                    val pos = result.positions[node.id] ?: return@map node
                    node.copy(x = pos.first, y = pos.second)
                }
                viewModel.replaceCurrentPipeline(uiState.currentPipeline.copy(nodes = nextNodes))
            },
            onRun = {
                viewModel.saveCurrentPipeline()
                viewModel.setRunning(running = !runState.isRunning)
            },
            onOverflow = {
                scope.launch { snackbarHostState.showSnackbar("More actions arrive post-v0.1.") }
            },
            onMoveNode = { nodeId, dxCanvas, dyCanvas ->
                editor.undoRedo.push(uiState.currentPipeline)
                viewModel.moveNode(nodeId, dxCanvas, dyCanvas)
            },
            onAddNode = { type, canvasX, canvasY ->
                editor.undoRedo.push(uiState.currentPipeline)
                viewModel.addNode(type, canvasX, canvasY)
                val justAdded = uiState.currentPipeline.nodes.lastOrNull()
                if (justAdded != null) {
                    editor.configuringNodeId = justAdded.id
                    editor.workingConfig = NodeConfigCodec.defaultFor(
                        type = NodeTypeMapper.toCatalog(type),
                        title = justAdded.label.ifBlank { type.name },
                    )
                }
            },
            onAddConnection = { sourceId, targetId ->
                editor.undoRedo.push(uiState.currentPipeline)
                viewModel.addConnection(sourceId, targetId)
            },
            onFocusNode = viewModel::requestFocusNode,
            onMultiSelectCancel = {
                editor.multiSelectMode = false
                editor.selection = emptySet()
            },
            onMultiSelectDelete = {
                if (editor.selection.isEmpty()) return@PipelineEditorContent
                editor.undoRedo.push(uiState.currentPipeline)
                editor.selection.forEach { id -> viewModel.removeNode(id) }
                editor.selection = emptySet()
                editor.multiSelectMode = false
            },
            activeRunningNodeLabel = runState.activeNodeId?.let { id ->
                uiState.currentPipeline.nodes.find { it.id == id }?.label
            },
            activeRunningEdgeIds = activeRunningEdges(runState.activeNodeId, uiState.currentPipeline),
            modifier = Modifier.fillMaxSize(),
        )

        val sheetNodeId = editor.configuringNodeId
        val workingConfig = editor.workingConfig
        if (sheetNodeId != null && workingConfig != null) {
            val node = uiState.currentPipeline.nodes.find { it.id == sheetNodeId }
            if (node != null) {
                val peerTitles = remember(uiState.currentPipeline.nodes, node.id) {
                    uiState.currentPipeline.nodes
                        .filter { it.id != node.id }
                        .map { it.label }
                        .toSet()
                }
                NodeConfigSheetHost(
                    config = workingConfig,
                    peerTitles = peerTitles,
                    onChange = { next -> editor.workingConfig = next },
                    onCancel = {
                        editor.configuringNodeId = null
                        editor.workingConfig = null
                    },
                    onSave = { saved ->
                        val mutated = NodeConfigCodec.apply(node, saved)
                        editor.undoRedo.push(uiState.currentPipeline)
                        viewModel.updateNodeFromEditor(node.id, mutated)
                        editor.configuringNodeId = null
                        editor.workingConfig = null
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Identifies the set of edge ids that should animate in run-trace mode. */
private fun activeRunningEdges(activeNodeId: String?, graph: PipelineGraph): Set<String> {
    if (activeNodeId == null) return emptySet()
    return graph.connections.filter { it.targetNodeId == activeNodeId }.map { it.id }.toSet()
}

/** Re-exported so callers (nav-graph) can refer to a typed action set without inlining `NodeType`. */
typealias EditorAddNodeCallback = (NodeType, Float, Float) -> Unit
