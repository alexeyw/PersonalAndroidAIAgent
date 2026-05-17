package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.R
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.common.asString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.components.chips.Status
import app.knotwork.design.screens.pipelines.PipelineLibraryCallbacks
import app.knotwork.design.screens.pipelines.PipelineLibraryContent
import app.knotwork.design.screens.pipelines.PipelineLibraryFab
import app.knotwork.design.screens.pipelines.PipelineLibraryFilter
import app.knotwork.design.screens.pipelines.PipelineLibraryRow
import app.knotwork.design.screens.pipelines.PipelineLibraryViewState
import app.knotwork.design.screens.pipelines.PipelineLibraryVisualState
import app.knotwork.design.screens.pipelines.PipelineSecondaryLineKind
import app.knotwork.design.screens.pipelines.isFabHidden
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
    onOpenEditor: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PipelineLibraryFilter.All) }
    var openOverflowRowId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PipelineGraph?>(null) }
    var deleteTarget by remember { mutableStateOf<PipelineGraph?>(null) }

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

    val filteredRows by remember(rows, searchQuery, activeFilter) {
        derivedStateOf {
            val byFilter = when (activeFilter) {
                PipelineLibraryFilter.All, PipelineLibraryFilter.Mine -> rows
                // Recent: top-N by last-modified order — repository already
                // returns pipelines newest-first.
                PipelineLibraryFilter.Recent -> rows.take(n = RECENT_TAKE_COUNT)
                // Shared is rendered disabled in the chip row; the screen
                // never sets `activeFilter = Shared`, but if it ever did
                // we'd surface the empty result deterministically.
                PipelineLibraryFilter.Shared -> emptyList()
            }
            val q = searchQuery.trim()
            if (q.isEmpty()) byFilter else byFilter.filter { it.title.contains(q, ignoreCase = true) }
        }
    }

    val visualState = when {
        uiState.errorMessage != null && rows.isEmpty() -> PipelineLibraryVisualState.Error
        uiState.isLoading && rows.isEmpty() -> PipelineLibraryVisualState.Loading
        rows.isEmpty() -> PipelineLibraryVisualState.Empty
        searchQuery.isNotBlank() || activeFilter != PipelineLibraryFilter.All ->
            PipelineLibraryVisualState.Filtering
        else -> PipelineLibraryVisualState.Populated
    }

    val viewState = PipelineLibraryViewState(
        visualState = visualState,
        pipelines = if (visualState == PipelineLibraryVisualState.Filtering) filteredRows else rows,
        totalCount = rows.size,
        defaultCount = if (uiState.defaultPipelineId != null) 1 else 0,
        searchQuery = searchQuery,
        activeFilter = activeFilter,
        errorMessage = if (visualState == PipelineLibraryVisualState.Error) errorText.orEmpty() else null,
        openOverflowRowId = openOverflowRowId,
    )

    val callbacks = PipelineLibraryCallbacks(
        onSearchQueryChange = { searchQuery = it },
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
        onOpenSearch = {
            // Reveal the inline chrome by seeding the query with an empty
            // string so the show-search-chrome predicate fires.
            if (searchQuery.isEmpty()) searchQuery = "" // no-op, but explicit
            activeFilter = PipelineLibraryFilter.All
            searchQuery = " " // single-space sentinel that disappears as the user types
        },
        onTopOverflow = { /* top overflow ships post-v0.1. */ },
        onNewPipeline = { showCreateDialog = true },
        onBrowseTemplates = {
            scope.launch { snackbarHostState.showSnackbar(message = TEMPLATES_COMING_SOON_MESSAGE) }
        },
        onClearSearch = { searchQuery = "" },
        onErrorRetry = { viewModel.clearError() },
    )

    Box(modifier = Modifier.fillMaxSize().testTag(tag = LIBRARY_ROOT_TEST_TAG)) {
        PipelineLibraryContent(state = viewState, callbacks = callbacks)
        // Mockup places the pill FAB so its bottom half overlaps the
        // bottom-nav strip (~64 dp tall). The catalog FAB does the brand
        // styling; this overlay owns positioning. `offset(y = …)` shifts
        // the pill down past the AppShellScaffold's content box so it
        // sits on top of the nav bar.
        if (!viewState.isFabHidden) {
            PipelineLibraryFab(
                onClick = callbacks.onNewPipeline,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FAB_END_PADDING)
                    .offset(y = FAB_BOTTOM_OVERLAP_OFFSET),
            )
        }
        SnackbarHost(hostState = snackbarHostState)
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.orchestrator_library_name_field_label)) },
                singleLine = true,
                modifier = Modifier.testTag(tag = "pipeline_name_field"),
            )
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
    val flavour = nodes
        .asSequence()
        .map { it.type.name }
        .take(n = NODE_FLOW_PREVIEW_COUNT)
        .joinToString(separator = "→")
        .ifBlank { "empty pipeline" }
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
        leadingIcon = Icons.Outlined.AccountTree,
        isActive = isActive,
        isDefault = isDefault,
    )
}

/** Pre-formatted "n nodes" segment used in the row subtitle. */
private val PipelineGraph.nodeCountText: String
    get() = if (nodes.size == 1) "1 node" else "${nodes.size} nodes"

/** Number of rows considered "recent" by the Recent filter chip. */
private const val RECENT_TAKE_COUNT = 3

/** Number of node types listed in the "8 nodes · INPUT→PLANNER→TOOLS→OUTPUT" subtitle. */
private const val NODE_FLOW_PREVIEW_COUNT = 4

/** Packed ARGB of the leading-mark tint used by every library row (brand orange). */
private const val LEADING_TINT_PACKED: Long = 0xFFC48225

/** TestTag applied to the screen root so Espresso / Compose tests can anchor. */
internal const val LIBRARY_ROOT_TEST_TAG = "pipeline_library_root"

/** Snackbar message shown when the user taps "Browse templates" before the gallery lands. */
private const val TEMPLATES_COMING_SOON_MESSAGE = "Template gallery ships after v0.1."

/** Snackbar message shown when the user taps "Export JSON" — per-id export lands post-v0.1. */
private const val EXPORT_COMING_SOON_MESSAGE = "Per-pipeline export ships in a follow-up."

/** Snackbar message shown when the user taps the footer "Import JSON" link. */
private const val IMPORT_HINT_MESSAGE = "Use the import dialog in the editor for now."

/** Inset from the right edge for the floating "+ New pipeline" FAB. */
private val FAB_END_PADDING = 16.dp

/**
 * Vertical offset applied to the FAB so it overlaps the bottom navigation
 * strip by roughly half its height — matches the mockup pose where the
 * pill's bottom edge tucks under the nav-bar baseline.
 */
private val FAB_BOTTOM_OVERLAP_OFFSET = 24.dp
