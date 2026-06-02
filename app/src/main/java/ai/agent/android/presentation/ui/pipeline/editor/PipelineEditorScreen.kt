package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.resolve
import ai.agent.android.presentation.ui.components.PromptPreviewBottomSheet
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.PromptPreviewState
import ai.agent.android.presentation.ui.orchestrator.components.NodeContextConfigSection
import ai.agent.android.presentation.ui.orchestrator.components.PromptPresetPickerDialog
import ai.agent.android.presentation.ui.orchestrator.components.SavePromptAsPresetDialog
import ai.agent.android.presentation.ui.orchestrator.presets.PipelinePresetsViewModel
import ai.agent.android.presentation.ui.orchestrator.presets.PresetPickerSheet
import ai.agent.android.presentation.ui.orchestrator.presets.SaveAsPresetDialog
import ai.agent.android.presentation.ui.pipeline.editor.canvas.formatScalePercent
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeConfigCodec
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import ai.agent.android.presentation.ui.pipeline.editor.core.AutoLayout
import ai.agent.android.presentation.ui.pipeline.editor.core.Bounds
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
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
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
    // PromptPresetPickerDialog filtered by the node type, and on Apply call back `apply`
    // (the form's "set this field" lambda). Stays as a single state because only one library
    // request can be pending at a time (the sheet is modal and the dialog stacks on top).
    var pendingLibrary by remember { mutableStateOf<PendingPromptLibrary?>(null) }
    // When the user taps the 💾 button on a prompt-bearing field, the catalog form invokes
    // onSavePreset(category, currentPrompt). We stash both here and render
    // SavePromptAsPresetDialog; on confirm the VM dispatches SavePromptAsPresetUseCase.
    var pendingSavePreset by remember { mutableStateOf<PendingSavePromptPreset?>(null) }
    // Overflow DropdownMenu visibility — opened from the toolbar's overflow callback,
    // dismissed by tap-outside or by clicking any menu item.
    var overflowOpen by remember { mutableStateOf(false) }
    // Rename-node dialog state — set to the target node id when the user picks
    // "Rename node…" from the overflow menu (requires exactly one node selected).
    var pendingRenameNodeId by remember { mutableStateOf<String?>(null) }
    // Save-as-preset dialog state — true while the dialog is visible, dismissed
    // on cancel or after submission.
    var saveAsPresetOpen by remember { mutableStateOf(false) }
    // Preset-picker sheet visibility — opened from the empty-pipeline state's
    // "From template" CTA. On pick, the preset is materialised into a fresh
    // pipeline and the editor swaps onto it (see the `pendingPipelineIdFromPreset`
    // effect below).
    var showPresetPicker by remember { mutableStateOf(false) }
    // Screen-local clock driving the [RunStatusBanner] elapsed-seconds metric.
    // Reset to 0 every time a new run starts; ticks ~10 Hz while running so the
    // banner reads ~"4.2 s" rather than jumping by whole seconds. Stops when
    // `runState.isRunning` flips back to false.
    var runElapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(runState) {
        editor.isRunning = runState.isRunning
        editor.activeRunningNodeId = runState.activeNodeId
    }
    // Reset run state on screen leave so the banner doesn't stick around when
    // the user navigates back to the library or switches pipelines. The
    // OrchestratorViewModel is scoped to the `pipelines` nested nav-graph
    // (shared with the library), so its StateFlow survives the editor screen
    // and would otherwise re-render the banner on the next open.
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopRunAndReset() }
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
                editor.workingContextConfig = null
            }
            editor.searchOpen -> {
                // System back is the natural "close search" gesture; the bar's
                // own × button stays as a discoverable alternative. Without
                // this branch, system back was falling through to `onBack` and
                // dragging the user out of the editor entirely.
                editor.searchOpen = false
                editor.searchQuery = ""
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
        val saveDoneMessage = stringResource(R.string.pipeline_editor_save_done)
        val runPreviewMessage = stringResource(R.string.pipeline_editor_run_preview)
        // Banner shows Running while the orchestrator reports the run is live; falls
        // back to Idle (banner hidden) otherwise. Done / Paused variants land when
        // real run-completion telemetry arrives (out of scope for Phase 22 / Task
        // 14 — engine wiring is tracked separately).
        //
        // The `remember` key set MUST include every `runState` field the derived
        // calculation reads, otherwise the banner's step-counter sticks at the
        // first value seen for a given run. `activeNodeId` in particular changes
        // mid-run while `isRunning` stays true — leaving it out of the key would
        // freeze `stepIndex` at the initial node index for the entire run.
        val runStatus by remember(
            runState.isRunning,
            runState.activeNodeId,
            pipeline.nodes.size,
        ) {
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
        val onAutoLayoutClick: () -> Unit = autoLayoutClick@{
            editor.undoRedo.push(pipeline)
            val result = AutoLayout.compute(pipeline)
            if (result.positions.isEmpty()) return@autoLayoutClick
            // `AutoLayout.compute` emits coordinates anchored at the canvas
            // origin (`(0, 0)` for the seed layer); without an offset the
            // re-laid graph lands in the upper-left corner of the canvas
            // regardless of where the user was looking, which reads as "the
            // editor swallowed my nodes". Translate the freshly computed bbox
            // so its centre lands on the centre of the previously occupied
            // bbox — the user stays put visually and the layout simply
            // tightens around their viewport.
            val originalBbox = Bounds.ofNodes(
                positions = pipeline.nodes.map { it.x to it.y },
                nodeWidth = NODE_CARD_WIDTH_PX,
                nodeHeight = NODE_CARD_HEIGHT_PX,
            )
            val computedBbox = Bounds.ofNodes(
                positions = result.positions.values.toList(),
                nodeWidth = NODE_CARD_WIDTH_PX,
                nodeHeight = NODE_CARD_HEIGHT_PX,
            )
            val dx = if (originalBbox != null && computedBbox != null) {
                (originalBbox.minX + originalBbox.maxX) / 2f -
                    (computedBbox.minX + computedBbox.maxX) / 2f
            } else {
                0f
            }
            val dy = if (originalBbox != null && computedBbox != null) {
                (originalBbox.minY + originalBbox.maxY) / 2f -
                    (computedBbox.minY + computedBbox.maxY) / 2f
            } else {
                0f
            }
            val nextNodes = pipeline.nodes.map { node ->
                val pos = result.positions[node.id] ?: return@map node
                node.copy(
                    x = CanvasTransform.snapToGrid(pos.first + dx),
                    y = CanvasTransform.snapToGrid(pos.second + dy),
                )
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
                // Fully reset isRunning + activeNodeId so the banner / per-node
                // dimming clear together. Using `stopRunAndReset` (not
                // `setRunning(false)`) so a paused-mid-run `activeNodeId` doesn't
                // leak across the next run.
                viewModel.stopRunAndReset()
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
                // Phase 22 / Task 14 ships only the UI-side run banner +
                // dimming + traveling-dot scaffolding. The real
                // `GraphExecutionEngine` wiring (actual node execution, token
                // streaming, tool dispatch, completion-state Done variant)
                // lands in a follow-up. Until then, `Run` flips the
                // `isRunning` flag so the user can see the banner / dimming
                // surfaces, and we surface a snackbar making the preview
                // status explicit — otherwise users tap Run, see the banner,
                // and rightly wonder why nothing else happens.
                viewModel.saveCurrentPipeline()
                viewModel.setRunning(running = !runState.isRunning)
                if (runState.isRunning.not()) {
                    // We just FLIPPED running to true above; in-line snackbar
                    // makes the "preview only" status discoverable.
                    scope.launch { snackbarHostState.showSnackbar(runPreviewMessage) }
                }
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
                // Fresh nodes get the all-enabled context config so every
                // available context block flows into the prompt by default.
                editor.workingContextConfig = NodeContextConfig.ALL_ENABLED
            },
            onAddConnection = { sourceId, targetId, label ->
                editor.undoRedo.push(pipeline)
                viewModel.addConnection(sourceId, targetId, label)
            },
            onOpenNodeConfig = { nodeId ->
                val target = pipeline.nodes.find { it.id == nodeId } ?: return@PipelineEditorContent
                editor.configuringNodeId = nodeId
                editor.workingConfig = NodeConfigCodec.decode(target)
                editor.workingContextConfig = target.contextConfig
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
                editor.workingContextConfig = NodeContextConfig.ALL_ENABLED
            },
            onFromTemplate = { showPresetPicker = true },
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
                // Explicit Save sits at the top of the overflow so the action is
                // discoverable. Most graph mutations currently update the in-memory
                // `currentPipeline` via `replaceCurrentPipeline` but don't persist
                // to disk; this item is the user's reliable "write to disk" lever.
                // Pressing Run also persists (via `saveCurrentPipeline` in the
                // primary-action callback) — the explicit Save just removes the
                // "did anything actually persist?" guesswork.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_save)) },
                    onClick = {
                        overflowOpen = false
                        viewModel.saveCurrentPipeline()
                        scope.launch { snackbarHostState.showSnackbar(saveDoneMessage) }
                    },
                    leadingIcon = {
                        Icon(AppIcons.Save, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_undo)) },
                    onClick = {
                        overflowOpen = false
                        onUndoClick()
                    },
                    enabled = editor.undoRedo.canUndo,
                    leadingIcon = {
                        Icon(AppIcons.Undo, contentDescription = null)
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
                        Icon(AppIcons.Redo, contentDescription = null)
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
                        Icon(AppIcons.Edit, contentDescription = null)
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
                        Icon(AppIcons.Trash, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pipeline_editor_overflow_save_as_preset)) },
                    onClick = {
                        overflowOpen = false
                        saveAsPresetOpen = true
                    },
                    leadingIcon = {
                        Icon(AppIcons.Bookmark, contentDescription = null)
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
                        Icon(AppIcons.Globe, contentDescription = null)
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
                            imageVector = if (editor.gridVisible) AppIcons.GridOff else AppIcons.Grid,
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
                        Icon(AppIcons.Search, contentDescription = null)
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
                            // `viewModel.addNode` + `updateNodeFromEditor` propagate through
                            // the orchestrator's StateFlow; we never need to thread a local
                            // graph snapshot through the loop because nothing in this block
                            // reads it after the dispatch. The loop is fire-and-forget per
                            // node and the next composition re-renders from the fresh VM state.
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
                            }
                        }
                    },
                    enabled = editor.clipboard.isNotEmpty(),
                    leadingIcon = {
                        Icon(AppIcons.Paste, contentDescription = null)
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
                        editor.workingContextConfig = null
                    },
                    onSave = { saved ->
                        val mutated = NodeConfigCodec.apply(node, saved)
                        // Preserve the user's edits to the per-node context
                        // flags (Original task / Chat history / Long-term
                        // memory / Tool results) which the catalog
                        // `NodeConfigSheet` doesn't model — they're tracked
                        // in `editor.workingContextConfig` and stitched
                        // back here.
                        val withContext = editor.workingContextConfig
                            ?.let { mutated.copy(contextConfig = it) }
                            ?: mutated
                        editor.undoRedo.push(pipeline)
                        viewModel.updateNodeFromEditor(node.id, withContext)
                        editor.configuringNodeId = null
                        editor.workingConfig = null
                        editor.workingContextConfig = null
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
                    onPickFromLibrary = { category, currentPrompt, apply ->
                        // Categories emitted by the catalog are always LLM-driven NodeType
                        // names (`"LITE_RT"` etc.); see NodeConfigForms — non-LLM forms
                        // never expose the 📚 button. Defensive `runCatching` so a future
                        // typo in the catalog doesn't crash the editor.
                        val type = runCatching { NodeType.valueOf(category) }.getOrNull()
                        if (type != null) {
                            pendingLibrary = PendingPromptLibrary(
                                nodeType = type,
                                currentPrompt = currentPrompt,
                                apply = apply,
                            )
                        }
                    },
                    onSavePreset = { category, currentPrompt ->
                        val type = runCatching { NodeType.valueOf(category) }.getOrNull()
                        if (type != null) {
                            pendingSavePreset = PendingSavePromptPreset(
                                nodeType = type,
                                systemPrompt = currentPrompt,
                            )
                        }
                    },
                    extraSection = {
                        // Bind the legacy `NodeContextConfigSection` ("Input
                        // Data" checkboxes) to `editor.workingContextConfig`
                        // — the catalog `NodeConfigSheet` doesn't model
                        // context flags (those are domain-level), so the
                        // production sheet adds them via the `extraSection`
                        // slot. Defaults to `ALL_ENABLED` if for any reason
                        // the per-open initialisation didn't run.
                        val ctx = editor.workingContextConfig
                            ?: NodeContextConfig.ALL_ENABLED
                        NodeContextConfigSection(
                            originalTask = ctx.originalTask,
                            chatHistory = ctx.chatHistory,
                            longTermMemory = ctx.longTermMemory,
                            toolResults = ctx.toolResults,
                            onOriginalTaskChange = { next ->
                                editor.workingContextConfig =
                                    ctx.copy(originalTask = next)
                            },
                            onChatHistoryChange = { next ->
                                editor.workingContextConfig =
                                    ctx.copy(chatHistory = next)
                            },
                            onLongTermMemoryChange = { next ->
                                editor.workingContextConfig =
                                    ctx.copy(longTermMemory = next)
                            },
                            onToolResultsChange = { next ->
                                editor.workingContextConfig =
                                    ctx.copy(toolResults = next)
                            },
                        )
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
            val bundled by viewModel
                .bundledPresetsForType(libraryRequest.nodeType)
                .collectAsState(initial = emptyList())
            val mine by viewModel
                .userPresetsForType(libraryRequest.nodeType)
                .collectAsState(initial = emptyList())
            PromptPresetPickerDialog(
                nodeType = libraryRequest.nodeType,
                bundled = bundled,
                mine = mine,
                currentPrompt = libraryRequest.currentPrompt,
                onApply = { picked ->
                    libraryRequest.apply(picked)
                    pendingLibrary = null
                },
                onPreview = { prompt -> viewModel.requestPromptPreview(prompt) },
                onDismiss = { pendingLibrary = null },
            )
        }

        val savePresetRequest = pendingSavePreset
        if (savePresetRequest != null) {
            SavePromptAsPresetDialog(
                nodeType = savePresetRequest.nodeType,
                systemPromptPreview = savePresetRequest.systemPrompt,
                onConfirm = { result ->
                    viewModel.saveCurrentPromptAsPreset(
                        systemPrompt = savePresetRequest.systemPrompt,
                        name = result.name,
                        description = result.description,
                        nodeType = savePresetRequest.nodeType,
                        tags = result.tags,
                    )
                    pendingSavePreset = null
                },
                onDismiss = { pendingSavePreset = null },
            )
        }

        // Prompt preview bottom sheet — driven by `previewState`. Rendered as long
        // as the picker dialog or any other surface in the editor requests a
        // preview (loading -> ready -> hidden). The sheet content is empty
        // (segments = null) until the engine finishes resolving variables.
        val previewState = uiState.previewState
        if (previewState !is PromptPreviewState.Hidden) {
            PromptPreviewBottomSheet(
                segments = (previewState as? PromptPreviewState.Ready)?.segments,
                onDismiss = { viewModel.dismissPromptPreview() },
            )
        }

        val renameNodeId = pendingRenameNodeId
        if (renameNodeId != null) {
            val node = pipeline.nodes.find { it.id == renameNodeId }
            if (node == null) {
                pendingRenameNodeId = null
            } else {
                var renameDraft by remember(renameNodeId) { mutableStateOf(node.label) }
                AlertDialog(
                    onDismissRequest = { pendingRenameNodeId = null },
                    title = { Text(text = stringResource(R.string.pipeline_editor_rename_title)) },
                    text = {
                        KnotworkField(
                            label = stringResource(R.string.pipeline_editor_rename_field_label),
                        ) {
                            KnotworkTextField(
                                value = renameDraft,
                                onValueChange = { renameDraft = it },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val trimmed = renameDraft.trim()
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

        if (saveAsPresetOpen) {
            SaveAsPresetDialog(
                initialName = pipeline.name,
                onDismiss = { saveAsPresetOpen = false },
                onConfirm = { result ->
                    viewModel.saveCurrentAsPreset(
                        name = result.name,
                        description = result.description,
                        category = result.category,
                        tags = result.tags,
                    )
                    saveAsPresetOpen = false
                },
            )
        }

        // "From template" picker — mirrors the library's `+ From preset` flow.
        // Picking a preset materialises it into a brand-new pipeline through
        // `LoadPipelineFromPresetUseCase`; the editor then swaps onto that
        // pipeline (no navigation hop — we're already here).
        //
        // The presets ViewModel is resolved lazily inside this branch (not as a
        // screen parameter) so the editor only touches Hilt when the picker is
        // actually opened — composable tests that drive the editor with a manual
        // OrchestratorViewModel and never open the picker stay Hilt-free.
        if (showPresetPicker) {
            val presetsViewModel: PipelinePresetsViewModel = hiltViewModel()
            val presetsState by presetsViewModel.uiState.collectAsState()
            PresetPickerSheet(
                state = presetsState,
                onTabSelected = presetsViewModel::selectTab,
                onCategorySelected = presetsViewModel::selectCategory,
                // The sheet stays open until the load resolves; the effect below
                // closes it once the new pipeline id is in hand (or surfaces an
                // error and leaves the sheet up to retry).
                onUsePreset = presetsViewModel::loadFromPreset,
                onDismiss = { showPresetPicker = false },
            )
            // On success, swap the editor onto the freshly materialised pipeline.
            // Unlike the library (which navigates into the editor), we are already
            // here — so we re-point the shared OrchestratorViewModel and close.
            LaunchedEffect(presetsState.pendingPipelineIdFromPreset) {
                presetsState.pendingPipelineIdFromPreset?.let { newPipelineId ->
                    presetsViewModel.consumePendingPipelineNavigation()
                    // The screen-local EditorState belongs to the graph we are
                    // leaving. Drop its undo/redo history and transient selection
                    // before switching pipelines — otherwise a stale Undo snapshot
                    // from the previous graph would clobber the loaded preset the
                    // moment the user hits Undo.
                    editor.undoRedo.reset()
                    editor.clearTransient()
                    viewModel.loadPipeline(newPipelineId)
                    showPresetPicker = false
                }
            }
            LaunchedEffect(presetsState.errorMessage) {
                val msg = presetsState.errorMessage ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(message = context.resolve(msg))
                presetsViewModel.clearError()
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
    //
    // The snackbar call runs in the effect's own coroutine scope (not the outer
    // `rememberCoroutineScope()`) so that when the user clears or changes their
    // selection quickly the pending snackbar is cancelled with the effect, instead
    // of accumulating overlapping toasts via an orphaned outer-scope coroutine.
    val selectedEdge = editor.selectedEdgeId
    val edgeSelectedHint = stringResource(R.string.pipeline_editor_edge_selected_hint)
    LaunchedEffect(selectedEdge) {
        if (selectedEdge != null) {
            snackbarHostState.showSnackbar(message = edgeSelectedHint)
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
 * Pending prompt-library request raised by the catalog sheet's 📚 button. [nodeType]
 * scopes which `PromptPreset`s show up in the picker; [currentPrompt] is the field's
 * current draft so the picker can mark the matching row as `CURRENT`; [apply] is the
 * form's "set this field" lambda, invoked when the user picks a preset.
 */
private data class PendingPromptLibrary(val nodeType: NodeType, val currentPrompt: String, val apply: (String) -> Unit)

/**
 * Pending save-as-prompt-preset request raised by the catalog sheet's 💾 button.
 * [systemPrompt] is the current draft captured at click time; [nodeType] is the
 * active node's type, both forwarded to `SavePromptAsPresetUseCase` on submit.
 */
private data class PendingSavePromptPreset(val nodeType: NodeType, val systemPrompt: String)

/** Identifies the set of edge ids that should animate in run-trace mode. */
private fun activeRunningEdges(activeNodeId: String?, graph: PipelineGraph): Set<String> {
    if (activeNodeId == null) return emptySet()
    return graph.connections.filter { it.targetNodeId == activeNodeId }.map { it.id }.toSet()
}

/** Run-banner clock tick (~10 Hz). One decimal place is enough for the elapsed-seconds metric. */
private const val RUN_BANNER_TICK_MS: Long = 100L

/** Nanoseconds per second — for the run-banner clock arithmetic. */
private const val NANOS_PER_SECOND: Float = 1_000_000_000f

/**
 * Canvas-space NodeCard width / height. Mirrors the catalog `NodeCardWidth = 168
 * dp` and `NodeCardMaxHeight = 96 dp`. Used by the auto-layout post-translate so
 * the bbox math matches what the canvas actually paints.
 */
private const val NODE_CARD_WIDTH_PX: Float = 168f
private const val NODE_CARD_HEIGHT_PX: Float = 96f
