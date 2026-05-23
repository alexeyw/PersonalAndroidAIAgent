package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.resolve
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.components.PromptLibraryDialog
import ai.agent.android.presentation.ui.pipeline.editor.canvas.formatScalePercent
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeConfigCodec
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import ai.agent.android.presentation.ui.pipeline.editor.core.AutoLayout
import ai.agent.android.presentation.ui.pipeline.editor.core.CanvasTransform
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import ai.agent.android.presentation.ui.pipeline.editor.core.ValidationAutoFix
import ai.agent.android.presentation.ui.pipeline.editor.core.rememberEditorState
import ai.agent.android.presentation.ui.pipeline.editor.sheet.NodeConfigSheetHost
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.pipelineeditor.EditorPrimaryAction
import app.knotwork.design.components.pipelineeditor.LocalModelOption
import app.knotwork.design.components.pipelineeditor.RunStatus
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase-21 production pipeline editor — the Knotwork-redesigned canvas surface.
 *
 * Replaces the legacy `VisualOrchestratorScreen`. Shares the `OrchestratorViewModel`
 * scoped to the `pipelines` nested nav-graph with the library screen so a freshly
 * created / loaded pipeline appears here without an extra read.
 *
 * Phase 22 / Task 14 reshapes the toolbar to the designer's `[← back] [title +
 * subtitle] [primary action] [overflow]` layout. The overflow `DropdownMenu`
 * lives here (not in the catalog atom) so the production surface can fold in
 * features (Fit to view, Toggle grid, Find node, …) without churning the
 * catalog API every iteration.
 *
 * Responsibilities:
 *  - Subscribes to the VM's `uiState` + `runState` + `focusNodeRequest` streams.
 *  - Owns the screen-local [EditorState] (selection / undo / drafts).
 *  - Computes the toolbar subtitle + primary-action variant from runState /
 *    validation / node count.
 *  - Hosts the [PipelineEditorContent] layout, the overflow menu, the catalog
 *    `NodeConfigSheet`, and the edge-removal confirm dialog.
 *  - Dispatches graph mutations back to the VM (which persists through `SavePipelineUseCase`).
 *
 * @param viewModel the shared orchestrator view model (graph-scoped).
 * @param onBack invoked when the user navigates back to the library. The editor surfaces
 * the back affordance via `BackHandler` AND the toolbar's leading back icon — system
 * back closes any open sheet or multi-select session first, then falls through to
 * this lambda.
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
    // Overflow DropdownMenu visibility — opened from the toolbar's overflow callback,
    // dismissed by tap-outside or by clicking any menu item.
    var overflowOpen by remember { mutableStateOf(false) }
    // Rename-node dialog state — set to the target node id when the user picks
    // "Rename node…" from the overflow menu (requires exactly one node selected).
    var pendingRenameNodeId by remember { mutableStateOf<String?>(null) }
    // Screen-local clock driving the [RunStatusBanner] elapsed-seconds metric.
    // Reset to 0 every time a new run starts; ticks ~10 Hz while running so the
    // banner reads ~"4.2 s" rather than jumping by whole seconds. Stops when
    // `runState.isRunning` flips back to false.
    var runElapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(runState) {
        editor.isRunning = runState.isRunning
        editor.activeRunningNodeId = runState.activeNodeId
    }
    LaunchedEffect(runState.isRunning) {
        if (runState.isRunning) {
            runElapsedSeconds = 0f
            val startNanos = System.nanoTime()
            while (true) {
                delay(RUN_BANNER_TICK_MS)
                runElapsedSeconds = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
            }
        }
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
        val pipeline = uiState.currentPipeline
        val toolbarSubtitle = rememberToolbarSubtitle(
            isRunning = runState.isRunning,
            activeNodeId = runState.activeNodeId,
            graph = pipeline,
            validationErrorCount = validationErrors.size,
            miniMapOpen = editor.miniMapOpen,
            scale = editor.transform.scale,
        )
        val toolbarPrimaryAction = if (runState.isRunning) {
            EditorPrimaryAction.None
        } else {
            EditorPrimaryAction.Run
        }
        val toolbarPrimaryActionEnabled = !runState.isRunning && validationErrors.isEmpty()
        // Pre-resolve snackbar copies at composition time so the action lambdas
        // never read from `Context` (the `LocalContextGetResourceValueCall` lint
        // rule forbids `context.getString` / `getQuantityString` from Composable
        // scope). Wrapping `getString` here is fine because `stringResource`
        // already does the work — we just close over the result.
        val autoFixNoneMessage = stringResource(R.string.pipeline_editor_validation_auto_fix_none)
        val pasteEmptyMessage = stringResource(R.string.pipeline_editor_overflow_paste_empty)
        val autoFixDoneMessage = stringResource(R.string.pipeline_editor_validation_auto_fix_done)
        // Banner shows Running while the orchestrator reports the run is live; falls
        // back to Idle (banner hidden) otherwise. Done / Paused variants land when
        // real run-completion telemetry arrives (out of scope for Phase 22 / Task
        // 14 — engine wiring is tracked separately).
        val runStatus by remember(runState.isRunning, pipeline.nodes.size) {
            derivedStateOf {
                if (runState.isRunning) {
                    val activeIndex = runState.activeNodeId
                        ?.let { id -> pipeline.nodes.indexOfFirst { it.id == id } }
                        ?.takeIf { it >= 0 }
                        ?.let { it + 1 }
                        ?: 1
                    RunStatus.Running(
                        stepIndex = activeIndex,
                        totalSteps = pipeline.nodes.size.takeIf { it > 0 },
                        elapsedSeconds = runElapsedSeconds,
                    )
                } else {
                    RunStatus.Idle
                }
            }
        }

        val onUndoClick: () -> Unit = {
            val previous = editor.undoRedo.undo(pipeline)
            if (previous != null) viewModel.replaceCurrentPipeline(previous)
        }
        val onRedoClick: () -> Unit = {
            val next = editor.undoRedo.redo(pipeline)
            if (next != null) viewModel.replaceCurrentPipeline(next)
        }
        val onDeleteClick: () -> Unit = {
            val edgeId = editor.selectedEdgeId
            when {
                edgeId != null -> {
                    editor.undoRedo.push(pipeline)
                    viewModel.removeConnection(edgeId)
                    editor.selectedEdgeId = null
                }
                editor.selection.isNotEmpty() -> {
                    editor.undoRedo.push(pipeline)
                    editor.selection.forEach { id -> viewModel.removeNode(id) }
                    editor.selection = emptySet()
                    editor.multiSelectMode = false
                }
            }
        }
        val onAutoLayoutClick: () -> Unit = {
            editor.undoRedo.push(pipeline)
            val result = AutoLayout.compute(pipeline)
            val nextNodes = pipeline.nodes.map { node ->
                val pos = result.positions[node.id] ?: return@map node
                node.copy(x = pos.first, y = pos.second)
            }
            viewModel.replaceCurrentPipeline(pipeline.copy(nodes = nextNodes))
        }

        PipelineEditorContent(
            graph = pipeline,
            editor = editor,
            validationErrors = validationErrors,
            validationLabels = validationLabels.map { context.resolve(it) },
            errorsByNodeId = emptyMap(),
            reducedMotion = KnotworkTheme.a11y.reducedMotion(),
            toolbarSubtitle = toolbarSubtitle,
            toolbarPrimaryAction = toolbarPrimaryAction,
            toolbarPrimaryActionEnabled = toolbarPrimaryActionEnabled,
            runStatus = runStatus,
            onRunPause = {
                // Pause semantics arrive with the real GraphExecutionEngine wiring;
                // surface a hint until then so the button isn't a silent no-op.
                scope.launch { snackbarHostState.showSnackbar("Pause arrives with the real run engine.") }
            },
            onRunResume = {
                scope.launch { snackbarHostState.showSnackbar("Resume arrives with the real run engine.") }
            },
            onRunStop = {
                viewModel.setRunning(running = false)
            },
            onRunTrace = {
                // Trace navigation will jump to the console pane (Chat home).
                // Until that route lands, surface a placeholder so the button is discoverable.
                scope.launch { snackbarHostState.showSnackbar("Trace navigation arrives with run telemetry.") }
            },
            onPipelineNameChange = { name ->
                viewModel.replaceCurrentPipeline(pipeline.copy(name = name))
            },
            onNavigateUp = onBack,
            onPrimaryAction = {
                viewModel.saveCurrentPipeline()
                viewModel.setRunning(running = !runState.isRunning)
            },
            onOverflow = { overflowOpen = true },
            onMoveNode = { nodeId, dxCanvas, dyCanvas ->
                editor.undoRedo.push(pipeline)
                viewModel.moveNode(nodeId, dxCanvas, dyCanvas)
            },
            onAddNode = { type, canvasX, canvasY ->
                editor.undoRedo.push(pipeline)
                // `addNode` returns the new id synchronously — reading
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
                editor.undoRedo.push(pipeline)
                viewModel.addConnection(sourceId, targetId, label)
            },
            onOpenNodeConfig = { nodeId ->
                val target = pipeline.nodes.find { it.id == nodeId } ?: return@PipelineEditorContent
                editor.configuringNodeId = nodeId
                editor.workingConfig = NodeConfigCodec.decode(target)
            },
            onLongPressEdge = { connectionId -> pendingEdgeDelete = connectionId },
            onStartWithInput = {
                // Place INPUT at canvas origin (0, 0). The radial menu pattern
                // typically anchors to where the user tapped — here the tap was
                // a button, not the canvas, so we fall back to a sensible
                // grid-aligned spot. The user can immediately drag it.
                editor.undoRedo.push(pipeline)
                val newId = viewModel.addNode(NodeType.INPUT, x = 0f, y = 0f)
                editor.configuringNodeId = newId
                editor.workingConfig = NodeConfigCodec.defaultFor(
                    type = NodeTypeMapper.toCatalog(NodeType.INPUT),
                    title = NodeType.INPUT.name,
                )
            },
            onFromTemplate = {
                // Template gallery is tracked separately; surface a hint so the
                // CTA isn't a silent no-op while we land the picker.
                scope.launch {
                    snackbarHostState.showSnackbar("Template gallery arrives in a follow-up task.")
                }
            },
            onFocusNode = viewModel::requestFocusNode,
            onAutoFix = {
                val outcome = ValidationAutoFix.apply(pipeline, validationErrors)
                if (outcome.unchanged) {
                    scope.launch { snackbarHostState.showSnackbar(autoFixNoneMessage) }
                } else {
                    editor.undoRedo.push(pipeline)
                    viewModel.replaceCurrentPipeline(outcome.graph)
                    scope.launch { snackbarHostState.showSnackbar(autoFixDoneMessage) }
                }
            },
            onMultiSelectCancel = {
                editor.multiSelectMode = false
                editor.selection = emptySet()
            },
            onMultiSelectCopy = {
                editor.clipboard = pipeline.nodes.filter { it.id in editor.selection }
                editor.multiSelectMode = false
                editor.selection = emptySet()
            },
            onMultiSelectDelete = {
                if (editor.selection.isEmpty()) return@PipelineEditorContent
                editor.undoRedo.push(pipeline)
                editor.selection.forEach { id -> viewModel.removeNode(id) }
                editor.selection = emptySet()
                editor.multiSelectMode = false
            },
            activeRunningEdgeIds = activeRunningEdges(runState.activeNodeId, pipeline),
            modifier = Modifier.fillMaxSize(),
        )

        // Overflow DropdownMenu — anchored to the top-end of the screen so it drops
        // down from under the toolbar's overflow icon. Wrapper Box gives the menu a
        // positioning anchor; the top offset matches the catalog `EditorToolbar`
        // two-line height (64 dp) so the menu opens flush with the icon's baseline,
        // and falls just slightly under the single-line variant (56 dp) where the
        // ~8 dp gap is visually acceptable.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = KnotworkTheme.spacing.sp1, top = 64.dp),
        ) {
            DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { overflowOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_undo)) },
                    onClick = {
                        overflowOpen = false
                        onUndoClick()
                    },
                    enabled = editor.undoRedo.canUndo,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_redo)) },
                    onClick = {
                        overflowOpen = false
                        onRedoClick()
                    },
                    enabled = editor.undoRedo.canRedo,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_rename)) },
                    onClick = {
                        overflowOpen = false
                        pendingRenameNodeId = editor.selection.singleOrNull()
                    },
                    enabled = editor.selection.size == 1,
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_delete)) },
                    onClick = {
                        overflowOpen = false
                        onDeleteClick()
                    },
                    enabled = editor.selection.isNotEmpty() || editor.selectedEdgeId != null,
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_auto_layout)) },
                    onClick = {
                        overflowOpen = false
                        onAutoLayoutClick()
                    },
                    leadingIcon = {
                        Icon(AppIcons.AutoLayout, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_mini_map)) },
                    onClick = {
                        overflowOpen = false
                        editor.miniMapOpen = !editor.miniMapOpen
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Map, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (editor.gridVisible) {
                                    R.string.pipeline_editor_overflow_toggle_grid_hide
                                } else {
                                    R.string.pipeline_editor_overflow_toggle_grid_show
                                },
                            ),
                        )
                    },
                    onClick = {
                        overflowOpen = false
                        editor.gridVisible = !editor.gridVisible
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (editor.gridVisible) Icons.Outlined.GridOff else Icons.Outlined.GridOn,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_find)) },
                    onClick = {
                        overflowOpen = false
                        editor.searchOpen = true
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_paste)) },
                    onClick = {
                        overflowOpen = false
                        if (editor.clipboard.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar(pasteEmptyMessage) }
                        } else {
                            editor.undoRedo.push(pipeline)
                            val offsetCanvas = CanvasTransform.GRID_PX * 2f
                            var working = pipeline
                            editor.clipboard.forEach { original ->
                                val newId = viewModel.addNode(
                                    original.type,
                                    x = original.x + offsetCanvas,
                                    y = original.y + offsetCanvas,
                                )
                                // Replace the freshly-added node's payload with the original's
                                // config so the paste preserves every field (label, prompt, etc.).
                                viewModel.updateNodeFromEditor(
                                    nodeId = newId,
                                    updated = original.copy(
                                        id = newId,
                                        x = original.x + offsetCanvas,
                                        y = original.y + offsetCanvas,
                                    ),
                                )
                                working = working
                            }
                        }
                    },
                    enabled = editor.clipboard.isNotEmpty(),
                    leadingIcon = {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                    },
                )
            }
        }

        val sheetNodeId = editor.configuringNodeId
        val workingConfig = editor.workingConfig
        if (sheetNodeId != null && workingConfig != null) {
            val node = pipeline.nodes.find { it.id == sheetNodeId }
            if (node != null) {
                val peerTitles = remember(pipeline.nodes, node.id) {
                    pipeline.nodes
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
                        editor.undoRedo.push(pipeline)
                        viewModel.updateNodeFromEditor(node.id, mutated)
                        editor.configuringNodeId = null
                        editor.workingConfig = null
                    },
                    availableToolIds = uiState.availableTools.map { it.name },
                    availableModels = uiState.availableLocalModels.map { model ->
                        // The catalog `LocalModelOption.id` is the canonical identifier
                        // written into `LiteRtConfig.modelId`. We use the model's `path`
                        // because the runtime path is what the LiteRT engine actually
                        // loads — and `NodeConfigCodec.deriveFromLegacy` already maps
                        // legacy `node.modelPath` into `LiteRtConfig.modelId`, so the
                        // catalog identifier stays consistent across read / write.
                        LocalModelOption(
                            id = model.path,
                            displayName = model.name,
                            isActive = model.isActive,
                        )
                    },
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

        val renameNodeId = pendingRenameNodeId
        if (renameNodeId != null) {
            val node = pipeline.nodes.find { it.id == renameNodeId }
            if (node == null) {
                pendingRenameNodeId = null
            } else {
                var renameDraft by remember(renameNodeId) {
                    mutableStateOf(TextFieldValue(node.label))
                }
                AlertDialog(
                    onDismissRequest = { pendingRenameNodeId = null },
                    title = { Text(text = stringResource(R.string.pipeline_editor_rename_title)) },
                    text = {
                        OutlinedTextField(
                            value = renameDraft,
                            onValueChange = { renameDraft = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.pipeline_editor_rename_field_label)) },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val trimmed = renameDraft.text.trim()
                            if (trimmed.isNotEmpty() && trimmed != node.label) {
                                editor.undoRedo.push(pipeline)
                                viewModel.updateNodeFromEditor(node.id, node.copy(label = trimmed))
                            }
                            pendingRenameNodeId = null
                        }) { Text(text = stringResource(R.string.pipeline_editor_rename_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRenameNodeId = null }) {
                            Text(text = stringResource(R.string.pipeline_editor_rename_cancel))
                        }
                    },
                )
            }
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
            scope.launch { snackbarHostState.showSnackbar(message = edgeSelectedHint) }
        }
    }
}

/**
 * Computes the [PipelineEditorContent] subtitle from the live run / validation / graph
 * state. Pure-Compose (read-only) — no side effects.
 *
 * Priority: running > overview > issues > editing. "Done-after-run" subtitle
 * lands when run-completion telemetry exists (engine wiring is separate scope).
 */
@Composable
@Suppress("LongParameterList")
// Each input is independent state; bundling into a data class would only obscure the contract.
private fun rememberToolbarSubtitle(
    isRunning: Boolean,
    activeNodeId: String?,
    graph: PipelineGraph,
    validationErrorCount: Int,
    miniMapOpen: Boolean,
    scale: Float,
): String = when {
    isRunning -> {
        val activeLabel = activeNodeId?.let { id -> graph.nodes.find { it.id == id }?.label }
        if (activeLabel != null) {
            stringResource(R.string.pipeline_editor_subtitle_running, activeLabel)
        } else {
            stringResource(R.string.pipeline_editor_subtitle_running_idle)
        }
    }
    miniMapOpen -> stringResource(
        R.string.pipeline_editor_subtitle_overview,
        formatScalePercent(scale),
        graph.nodes.size,
    )
    validationErrorCount > 0 -> pluralStringResource(
        R.plurals.pipeline_editor_subtitle_issues,
        validationErrorCount,
        validationErrorCount,
    )
    else -> stringResource(
        R.string.pipeline_editor_subtitle_editing,
        graph.nodes.size,
        graph.connections.size,
    )
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

/** Run-banner clock tick (~10 Hz). One decimal place is enough for the elapsed-seconds metric. */
private const val RUN_BANNER_TICK_MS: Long = 100L

/** Nanoseconds per second — for the run-banner clock arithmetic. */
private const val NANOS_PER_SECOND: Float = 1_000_000_000f
