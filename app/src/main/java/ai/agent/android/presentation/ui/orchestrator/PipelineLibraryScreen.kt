package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.R
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.asString
import ai.agent.android.presentation.ui.orchestrator.presets.GraphFlowPreview
import ai.agent.android.presentation.ui.orchestrator.presets.PipelineLibrarySpeedDial
import ai.agent.android.presentation.ui.orchestrator.presets.PipelinePresetsViewModel
import ai.agent.android.presentation.ui.orchestrator.presets.PresetPickerSheet
import ai.agent.android.presentation.ui.orchestrator.presets.SaveAsPresetDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.components.chips.Status
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.screens.pipelines.PipelineLibraryCallbacks
import app.knotwork.design.screens.pipelines.PipelineLibraryContent
import app.knotwork.design.screens.pipelines.PipelineLibraryFilter
import app.knotwork.design.screens.pipelines.PipelineLibraryRow
import app.knotwork.design.screens.pipelines.PipelineLibraryViewState
import app.knotwork.design.screens.pipelines.PipelineLibraryVisualState
import app.knotwork.design.screens.pipelines.PipelineSecondaryLineKind
import app.knotwork.design.screens.pipelines.isFabHidden
import app.knotwork.design.theme.KnotworkTheme
import kotlinx.coroutines.launch

/**
 * Library screen listing every saved pipeline. Acts as the entry point for
 * the orchestrator feature.
 *
 * Phase 21 / Task 10 rewrite (mockup-driven): the catalog
 * [PipelineLibraryContent] composable owns the visual surface; this screen
 * subscribes to [OrchestratorViewModel], projects `OrchestratorUiState` to
 * the catalog [PipelineLibraryViewState], and dispatches user-triggered
 * events back to the VM through a [PipelineLibraryCallbacks] bundle.
 *
 * @param viewModel Shared orchestrator view-model (parent-graph scoped).
 * @param onOpenEditor Navigation callback invoked after the active pipeline
 * has been switched (load / duplicate / create).
 * @param onBack Reserved for future use; kept in the signature so the
 * nav-graph wiring needs no changes when the back arrow lands inside the
 * catalog surface.
 */
@Suppress("UnusedParameter", "LongMethod") // onBack kept for nav-graph stability; body is a flat switch.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineLibraryScreen(
    viewModel: OrchestratorViewModel = hiltViewModel(),
    presetsViewModel: PipelinePresetsViewModel = hiltViewModel(),
    onOpenEditor: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val presetsState by presetsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeFilter by remember { mutableStateOf(PipelineLibraryFilter.All) }
    var openOverflowRowId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PipelineGraph?>(null) }
    var deleteTarget by remember { mutableStateOf<PipelineGraph?>(null) }
    var saveAsPresetTarget by remember { mutableStateOf<PipelineGraph?>(null) }
    var showPresetPicker by remember { mutableStateOf(false) }

    val errorText = uiState.errorMessage?.asString()
    LaunchedEffect(errorText) {
        errorText?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    val feedbackText = uiState.feedbackMessage?.asString()
    LaunchedEffect(feedbackText) {
        feedbackText?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }
    LaunchedEffect(uiState.pendingEditorNavigation) {
        if (uiState.pendingEditorNavigation) {
            viewModel.consumePendingEditorNavigation()
            onOpenEditor()
        }
    }

    val rows by remember(uiState.savedPipelines, uiState.activePipelineId, uiState.defaultPipelineId) {
        derivedStateOf {
            uiState.savedPipelines.map { pipeline ->
                pipeline.toLibraryRow(
                    isActive = pipeline.id == uiState.activePipelineId,
                    isDefault = pipeline.id == uiState.defaultPipelineId,
                )
            }
        }
    }

    val filteredRows by remember(rows, activeFilter) {
        derivedStateOf {
            when (activeFilter) {
                PipelineLibraryFilter.All, PipelineLibraryFilter.Mine -> rows
                // Recent: top-N by last-modified order — repository already
                // returns pipelines newest-first.
                PipelineLibraryFilter.Recent -> rows.take(n = RECENT_TAKE_COUNT)
                // Shared is rendered disabled in the chip row; the screen
                // never sets `activeFilter = Shared`, but if it ever did
                // we'd surface the empty result deterministically.
                PipelineLibraryFilter.Shared -> emptyList()
            }
        }
    }

    val visualState = when {
        uiState.errorMessage != null && rows.isEmpty() -> PipelineLibraryVisualState.Error
        uiState.isLoading && rows.isEmpty() -> PipelineLibraryVisualState.Loading
        rows.isEmpty() -> PipelineLibraryVisualState.Empty
        else -> PipelineLibraryVisualState.Populated
    }

    val viewState = PipelineLibraryViewState(
        visualState = visualState,
        pipelines = if (activeFilter != PipelineLibraryFilter.All) filteredRows else rows,
        totalCount = rows.size,
        defaultCount = if (uiState.defaultPipelineId != null) 1 else 0,
        activeFilter = activeFilter,
        errorMessage = if (visualState == PipelineLibraryVisualState.Error) errorText.orEmpty() else null,
        openOverflowRowId = openOverflowRowId,
    )

    val callbacks = PipelineLibraryCallbacks(
        onFilterChange = { activeFilter = it },
        onPipelineClick = { id ->
            viewModel.loadPipeline(pipelineId = id)
            onOpenEditor()
        },
        onPipelineOverflow = { id -> openOverflowRowId = id },
        onOverflowDismiss = { openOverflowRowId = null },
        onLoadInEditor = { id ->
            viewModel.loadPipeline(pipelineId = id)
            onOpenEditor()
        },
        onSetAsDefault = { id -> viewModel.setDefaultPipeline(pipelineId = id) },
        onRename = { id ->
            uiState.savedPipelines.firstOrNull { it.id == id }?.let { renameTarget = it }
        },
        onDuplicate = { id -> viewModel.duplicatePipeline(pipelineId = id) },
        onExportJson = {
            // Per-id export needs a domain hook that streams a specific
            // pipeline through `PipelineJsonSerializer`. Until that lands,
            // surface a snackbar so the affordance is visible.
            scope.launch { snackbarHostState.showSnackbar(message = EXPORT_COMING_SOON_MESSAGE) }
        },
        onImportJson = {
            scope.launch { snackbarHostState.showSnackbar(message = IMPORT_HINT_MESSAGE) }
        },
        // Archive: phase-21 has no archival table yet — treat as delete so
        // the affordance is exercised.
        onArchive = { id ->
            uiState.savedPipelines.firstOrNull { it.id == id }?.let { deleteTarget = it }
        },
        onDelete = { id ->
            uiState.savedPipelines.firstOrNull { it.id == id }?.let { deleteTarget = it }
        },
        onOpenDrawer = { /* drawer ships post-v0.1. */ },
        onTopOverflow = { /* top overflow ships post-v0.1. */ },
        onNewPipeline = { showCreateDialog = true },
        onBrowseTemplates = { showPresetPicker = true },
        onSaveAsPreset = { id ->
            uiState.savedPipelines.firstOrNull { it.id == id }?.let { saveAsPresetTarget = it }
        },
        onErrorRetry = { viewModel.clearError() },
    )

    Box(modifier = Modifier.fillMaxSize().testTag(tag = LIBRARY_ROOT_TEST_TAG)) {
        PipelineLibraryContent(state = viewState, callbacks = callbacks)
        if (!viewState.isFabHidden) {
            PipelineLibrarySpeedDial(
                onNewPipeline = callbacks.onNewPipeline,
                onFromPreset = { showPresetPicker = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = KnotworkTheme.spacing.sp4,
                        bottom = KnotworkTheme.spacing.sp4,
                    ),
            )
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showPresetPicker) {
        PresetPickerSheet(
            state = presetsState,
            onTabSelected = presetsViewModel::selectTab,
            onCategorySelected = presetsViewModel::selectCategory,
            onUsePreset = { id ->
                presetsViewModel.loadFromPreset(id)
                showPresetPicker = false
            },
            onDismiss = { showPresetPicker = false },
        )
    }

    LaunchedEffect(presetsState.pendingPipelineIdFromPreset) {
        presetsState.pendingPipelineIdFromPreset?.let { newPipelineId ->
            presetsViewModel.consumePendingPipelineNavigation()
            viewModel.loadPipeline(newPipelineId)
            onOpenEditor()
        }
    }

    val presetFeedback = presetsState.feedbackMessage?.asString()
    LaunchedEffect(presetFeedback) {
        presetFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            presetsViewModel.clearFeedback()
        }
    }
    val presetError = presetsState.errorMessage?.asString()
    LaunchedEffect(presetError) {
        presetError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            presetsViewModel.clearError()
        }
    }

    saveAsPresetTarget?.let { target ->
        SaveAsPresetDialog(
            initialName = target.name,
            onDismiss = { saveAsPresetTarget = null },
            onConfirm = { result ->
                viewModel.saveAsPresetFromLibrary(
                    pipelineId = target.id,
                    name = result.name,
                    description = result.description,
                    category = result.category,
                    tags = result.tags,
                )
                saveAsPresetTarget = null
            },
        )
    }

    if (showCreateDialog) {
        PipelineNameDialog(
            title = stringResource(R.string.orchestrator_library_new_pipeline_title),
            confirmLabel = stringResource(R.string.common_create),
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createNewPipeline(name = name)
                showCreateDialog = false
            },
        )
    }
    renameTarget?.let { target ->
        PipelineNameDialog(
            title = stringResource(R.string.orchestrator_library_rename_pipeline_title),
            confirmLabel = stringResource(R.string.common_save),
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renamePipeline(pipelineId = target.id, newName = name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.orchestrator_library_delete_pipeline_title)) },
            text = {
                Text(stringResource(R.string.orchestrator_library_delete_confirm, target.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePipeline(pipelineId = target.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Reusable name-input dialog for both "New pipeline" and "Rename pipeline"
 * flows. The Save / Create button is disabled when the trimmed text is
 * empty so the user cannot submit blank names.
 */
@Composable
private fun PipelineNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val canConfirm = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            KnotworkField(
                label = stringResource(R.string.orchestrator_library_name_field_label),
                modifier = Modifier.testTag(tag = "pipeline_name_field"),
            ) {
                KnotworkTextField(
                    value = name,
                    onValueChange = { name = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Projects a domain [PipelineGraph] onto a catalog [PipelineLibraryRow].
 * Builds the "N nodes · {flavour}" subtitle from the first few node types
 * and derives the secondary status line ("Active default" / "Idle" /
 * "unbound").
 */
private fun PipelineGraph.toLibraryRow(isActive: Boolean, isDefault: Boolean): PipelineLibraryRow {
    // Walk the graph from INPUT following connections (GraphFlowPreview) rather
    // than iterating `nodes` in insertion order — otherwise the subtitle reads
    // e.g. "INPUT→OUTPUT→LITE_RT" (storage order) while the editor renders the
    // true execution order "INPUT→LITE_RT→OUTPUT".
    val flavour = GraphFlowPreview.render(this)
    val subtitle = "$nodeCountText · $flavour"
    val secondaryLine = when {
        isActive && isDefault -> "Active default"
        isActive -> "Active"
        nodes.isEmpty() -> "unbound"
        else -> null
    }
    val secondaryKind = if (nodes.isEmpty() && !isActive) {
        PipelineSecondaryLineKind.Unbound
    } else {
        PipelineSecondaryLineKind.Default
    }
    return PipelineLibraryRow(
        id = id,
        title = name.ifBlank { "untitled" },
        subtitle = subtitle,
        secondaryLine = secondaryLine,
        secondaryLineKind = secondaryKind,
        status = if (isActive) Status.Running else Status.Idle,
        leadingTint = Color(color = LEADING_TINT_PACKED),
        leadingIcon = AppIcons.Branch,
        isActive = isActive,
        isDefault = isDefault,
    )
}

/** Pre-formatted "n nodes" segment used in the row subtitle. */
private val PipelineGraph.nodeCountText: String
    get() = if (nodes.size == 1) "1 node" else "${nodes.size} nodes"

/** Number of rows considered "recent" by the Recent filter chip. */
private const val RECENT_TAKE_COUNT = 3

/** Packed ARGB of the leading-mark tint used by every library row (brand orange). */
private const val LEADING_TINT_PACKED: Long = 0xFFC48225

/** TestTag applied to the screen root so Espresso / Compose tests can anchor. */
internal const val LIBRARY_ROOT_TEST_TAG = "pipeline_library_root"

/** Snackbar message shown when the user taps "Export JSON" — per-id export lands post-v0.1. */
private const val EXPORT_COMING_SOON_MESSAGE = "Per-pipeline export ships in a follow-up."

/** Snackbar message shown when the user taps the footer "Import JSON" link. */
private const val IMPORT_HINT_MESSAGE = "Use the import dialog in the editor for now."
