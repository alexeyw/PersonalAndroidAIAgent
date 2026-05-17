package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.resolve
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.components.PromptLibraryDialog
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    // Edge to confirm-and-delete on long-press, paired with the toolbar's tap-select-then-Delete
    // path. Two routes to the same action so users find at least one of them.
    var pendingEdgeDelete by remember { mutableStateOf<String?>(null) }
    // When the user taps the 📚 button on a prompt-bearing field inside NodeConfigSheet, the
    // catalog form invokes onPickFromLibrary(category, apply). We stash both here, render the
    // existing PromptLibraryDialog filtered by category, and on selection call back `apply`
    // (the form's "set this field" lambda). Stays as a single state because only one library
    // request can be pending at a time (the sheet is modal and the dialog stacks on top).
    var pendingLibrary by remember { mutableStateOf<PendingPromptLibrary?>(null) }

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
            editor.selectedEdgeId != null -> {
                editor.selectedEdgeId = null
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
                // The toolbar Delete handles edge OR node selection — edge first, then nodes.
                // Edge selection is exclusive with node selection (`EditorState.selectEdge`
                // clears `selection`), so this resolves unambiguously.
                val edgeId = editor.selectedEdgeId
                when {
                    edgeId != null -> {
                        editor.undoRedo.push(uiState.currentPipeline)
                        viewModel.removeConnection(edgeId)
                        editor.selectedEdgeId = null
                    }
                    editor.selection.isNotEmpty() -> {
                        editor.undoRedo.push(uiState.currentPipeline)
                        editor.selection.forEach { id -> viewModel.removeNode(id) }
                        editor.selection = emptySet()
                        editor.multiSelectMode = false
                    }
                }
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
                // `addNode` now returns the new id synchronously — reading
                // `uiState.currentPipeline.nodes.lastOrNull()` here would observe the
                // pre-update snapshot since the StateFlow hasn't propagated yet.
                val newId = viewModel.addNode(type, canvasX, canvasY)
                editor.configuringNodeId = newId
                editor.workingConfig = NodeConfigCodec.defaultFor(
                    type = NodeTypeMapper.toCatalog(type),
                    title = type.name,
                )
            },
            onAddConnection = { sourceId, targetId, label ->
                editor.undoRedo.push(uiState.currentPipeline)
                viewModel.addConnection(sourceId, targetId, label)
            },
            onOpenNodeConfig = { nodeId ->
                val target = uiState.currentPipeline.nodes.find { it.id == nodeId } ?: return@PipelineEditorContent
                editor.configuringNodeId = nodeId
                editor.workingConfig = NodeConfigCodec.decode(target)
            },
            onLongPressEdge = { connectionId -> pendingEdgeDelete = connectionId },
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
                    availableToolIds = uiState.availableTools.map { it.name },
                    onPickFromLibrary = { category, apply ->
                        pendingLibrary = PendingPromptLibrary(category = category, apply = apply)
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val libraryRequest = pendingLibrary
        if (libraryRequest != null) {
            PromptLibraryDialog(
                prompts = uiState.promptTemplates.filter { it.category == libraryRequest.category },
                onPromptSelected = { picked ->
                    libraryRequest.apply(picked)
                    pendingLibrary = null
                },
                onDismissRequest = { pendingLibrary = null },
            )
        }

        val edgeToDelete = pendingEdgeDelete
        if (edgeToDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingEdgeDelete = null },
                title = { Text(text = stringResource(R.string.pipeline_editor_remove_connection_title)) },
                text = { Text(text = stringResource(R.string.pipeline_editor_remove_connection_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        editor.undoRedo.push(uiState.currentPipeline)
                        viewModel.removeConnection(edgeToDelete)
                        editor.selectedEdgeId = null
                        pendingEdgeDelete = null
                    }) { Text(text = stringResource(R.string.pipeline_editor_remove_connection_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEdgeDelete = null }) {
                        Text(text = stringResource(R.string.pipeline_editor_remove_connection_cancel))
                    }
                },
            )
        }
    }

    // Surface a one-shot hint the first time the user selects an edge, so the toolbar
    // Delete path becomes discoverable. `LaunchedEffect(editor.selectedEdgeId)` fires
    // each time the selected edge changes — including from `null` to an id (tap-select).
    val selectedEdge = editor.selectedEdgeId
    val edgeSelectedHint = stringResource(R.string.pipeline_editor_edge_selected_hint)
    LaunchedEffect(selectedEdge) {
        if (selectedEdge != null) {
            snackbarHostState.showSnackbar(message = edgeSelectedHint)
        }
    }
}

/**
 * Pending prompt-library request raised by the catalog sheet's 📚 button. [category]
 * scopes which `PromptTemplate`s show up in the picker; [apply] is the form's "set this
 * field" lambda, invoked when the user picks a prompt.
 */
private data class PendingPromptLibrary(val category: String, val apply: (String) -> Unit)

/** Identifies the set of edge ids that should animate in run-trace mode. */
private fun activeRunningEdges(activeNodeId: String?, graph: PipelineGraph): Set<String> {
    if (activeNodeId == null) return emptySet()
    return graph.connections.filter { it.targetNodeId == activeNodeId }.map { it.id }.toSet()
}

/** Re-exported so callers (nav-graph) can refer to a typed action set without inlining `NodeType`. */
typealias EditorAddNodeCallback = (NodeType, Float, Float) -> Unit
