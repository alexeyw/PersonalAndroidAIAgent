package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.PipelineGraph
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Library screen listing every saved pipeline. Acts as the entry point for
 * the orchestrator feature: tapping a pipeline loads it as the active one
 * and opens the visual editor; the FAB creates a new pipeline; the per-row
 * `⋮` menu offers Load / Rename / Duplicate / Delete actions.
 *
 * Shares [OrchestratorViewModel] with [VisualOrchestratorScreen] via a parent
 * nav-graph scope so creating, renaming, or duplicating a pipeline here is
 * immediately reflected when the editor opens.
 *
 * @param viewModel Shared orchestrator view-model (parent-graph scoped).
 * @param onOpenEditor Navigation callback invoked after the active pipeline
 * has been switched (load / duplicate / create) so the caller can transition
 * to the canvas editor.
 * @param onBack Navigation callback for the TopAppBar back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineLibraryScreen(
    viewModel: OrchestratorViewModel = hiltViewModel(),
    onOpenEditor: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PipelineGraph?>(null) }
    var deleteTarget by remember { mutableStateOf<PipelineGraph?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }
    // Navigate to the editor only when the ViewModel signals a successful
    // create. A failed create (validation, persistence error) keeps the flag
    // false, so the user stays on the library and can retry.
    LaunchedEffect(uiState.pendingEditorNavigation) {
        if (uiState.pendingEditorNavigation) {
            viewModel.consumePendingEditorNavigation()
            onOpenEditor()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pipelines") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("library_new_pipeline_fab"),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New pipeline")
            }
        },
    ) { paddingValues ->
        if (uiState.savedPipelines.isEmpty()) {
            EmptyLibraryState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.savedPipelines, key = { it.id }) { pipeline ->
                    PipelineRow(
                        pipeline = pipeline,
                        isActive = pipeline.id == uiState.activePipelineId,
                        onLoad = {
                            viewModel.loadPipeline(pipeline.id)
                            onOpenEditor()
                        },
                        onRename = { renameTarget = pipeline },
                        onDuplicate = { viewModel.duplicatePipeline(pipeline.id) },
                        onDelete = { deleteTarget = pipeline },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        PipelineNameDialog(
            title = "New pipeline",
            confirmLabel = "Create",
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                // Navigation to the editor is intentionally deferred: it fires
                // from the `pendingEditorNavigation` LaunchedEffect above only
                // after the ViewModel reports a successful create. A failed
                // create (e.g. >60-char name slipped past the dialog, or an
                // I/O error) leaves the user on the library to retry.
                viewModel.createNewPipeline(name)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { target ->
        PipelineNameDialog(
            title = "Rename pipeline",
            confirmLabel = "Save",
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renamePipeline(target.id, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete pipeline") },
            text = {
                Text(
                    "Delete pipeline «${target.name}»? This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePipeline(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Single row in the library list. Long-press or the `⋮` icon expands the
 * context menu; a simple tap loads the pipeline and exits to the editor.
 *
 * The leading 4dp accent stripe + the "Active" subtitle communicate which
 * pipeline is currently loaded into the editor; comparison is by id, not
 * by name, so duplicates with the same name remain distinguishable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PipelineRow(
    pipeline: PipelineGraph,
    isActive: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pipeline_row_${pipeline.id}")
            .combinedClickable(
                onClick = onLoad,
                onLongClick = { menuExpanded = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = pipeline.name.ifBlank { "Unnamed (${pipeline.id.take(4)})" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isActive) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Updated ${formatTimestamp(pipeline.updatedAt)} · ${pipeline.nodes.size} nodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("pipeline_row_menu_${pipeline.id}"),
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Pipeline actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Load") },
                        onClick = {
                            menuExpanded = false
                            onLoad()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        enabled = !isActive,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Reusable name-input dialog for both "New pipeline" and "Rename pipeline"
 * flows. The Save / Create button is disabled when the trimmed text is
 * empty so the user cannot submit blank names — defence-in-depth, since
 * the use cases also reject them.
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
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.testTag("pipeline_name_field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = canConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Empty-list placeholder displayed when no pipelines have been saved yet.
 * The FAB stays the canonical "create" entry point so this view is purely
 * informational.
 */
@Composable
private fun EmptyLibraryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No pipelines yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Tap + to create your first pipeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats a Unix-millis timestamp as `dd MMM yyyy HH:mm` in the device locale.
 * Kept alongside the screen because the format is library-specific (a
 * compact one-liner that fits a single row); other screens use longer
 * variants.
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
