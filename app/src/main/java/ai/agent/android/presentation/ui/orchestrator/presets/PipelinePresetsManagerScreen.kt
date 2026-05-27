package ai.agent.android.presentation.ui.orchestrator.presets

import ai.agent.android.R
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.presentation.ui.common.asString
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.theme.KnotworkTheme
import kotlinx.coroutines.launch

/**
 * Full-screen manager for bundled and user-saved pipeline presets.
 *
 * Layout:
 *
 *  - `TopAppBar` with a back button.
 *  - Bundled section (read-only) — every preset row shows name + category
 *    + bundled badge; no overflow.
 *  - User section — each row has a `⋮` overflow menu with Rename / Export
 *    JSON / Delete.
 *
 * Export uses `ActivityResultContracts.CreateDocument("application/json")`
 * so the user picks the destination through SAF; the serialised JSON
 * blob is written via the matching `ContentResolver.openOutputStream`.
 *
 * @param onBack Invoked when the user taps the back button.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelinePresetsManagerScreen(viewModel: PipelinePresetsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var renameTarget by remember { mutableStateOf<PipelinePreset?>(null) }
    var deleteTarget by remember { mutableStateOf<PipelinePreset?>(null) }
    var pendingExportPreset by remember { mutableStateOf<PipelinePreset?>(null) }

    val errorText = state.errorMessage?.asString()
    LaunchedEffect(errorText) {
        errorText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }
    val feedbackText = state.feedbackMessage?.asString()
    LaunchedEffect(feedbackText) {
        feedbackText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = EXPORT_MIME_TYPE),
    ) { uri ->
        val preset = pendingExportPreset
        pendingExportPreset = null
        if (uri == null || preset == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(viewModel.exportPresetToJson(preset).toByteArray())
                }
            }.onFailure { e ->
                snackbarHostState.showSnackbar(e.message ?: EXPORT_FAILED_FALLBACK)
            }.onSuccess {
                snackbarHostState.showSnackbar(EXPORT_SUCCESS_FALLBACK)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag = MANAGER_ROOT_TEST_TAG),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orchestrator_preset_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        ) {
            item(key = "bundled_header") {
                SectionHeader(stringResource(R.string.orchestrator_preset_manager_section_bundled))
            }
            if (state.bundledPresets.isEmpty()) {
                item(key = "bundled_empty") {
                    EmptyLine(stringResource(R.string.orchestrator_preset_manager_bundled_empty))
                }
            } else {
                items(items = state.bundledPresets, key = { "b_${it.id}" }) { preset ->
                    PresetManagerRow(
                        preset = preset,
                        onRename = null,
                        onDelete = null,
                        onExport = {
                            pendingExportPreset = preset
                            exportLauncher.launch(defaultFileName(preset))
                        },
                    )
                }
            }
            item(key = "user_header") {
                SectionHeader(stringResource(R.string.orchestrator_preset_manager_section_user))
            }
            if (state.userPresets.isEmpty()) {
                item(key = "user_empty") {
                    EmptyLine(stringResource(R.string.orchestrator_preset_manager_user_empty))
                }
            } else {
                items(items = state.userPresets, key = { "u_${it.id}" }) { preset ->
                    PresetManagerRow(
                        preset = preset,
                        onRename = { renameTarget = preset },
                        onDelete = { deleteTarget = preset },
                        onExport = {
                            pendingExportPreset = preset
                            exportLauncher.launch(defaultFileName(preset))
                        },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenamePresetDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameUserPreset(presetId = target.id, newName = newName)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.orchestrator_preset_manager_delete_title)) },
            text = { Text(stringResource(R.string.orchestrator_preset_manager_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUserPreset(target.id)
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

/** Bundled / User section header. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

/** Inline empty-state line for an empty section. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

/**
 * One preset row in the manager. Bundled rows pass `null` for [onRename]
 * and [onDelete] so the overflow only surfaces the Export action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetManagerRow(
    preset: PipelinePreset,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag = managerRowTestTag(preset.id))
            .padding(vertical = KnotworkTheme.spacing.sp1),
    ) {
        Column(modifier = Modifier.padding(end = KnotworkTheme.spacing.sp6)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = GraphFlowPreview.render(preset.graph),
                style = MaterialTheme.typography.labelSmall,
                color = KnotworkTheme.extended.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag(tag = managerOverflowTestTag(preset.id)),
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (onRename != null) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                    text = { Text(stringResource(R.string.orchestrator_preset_manager_action_rename)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
            }
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                text = { Text(stringResource(R.string.orchestrator_preset_manager_action_export)) },
                onClick = {
                    menuOpen = false
                    onExport()
                },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = KnotworkTheme.extended.signalError,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.orchestrator_preset_manager_action_delete),
                            color = KnotworkTheme.extended.signalError,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Rename dialog wired by the manager screen. */
@Composable
private fun RenamePresetDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val canConfirm = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.orchestrator_preset_manager_rename_title)) },
        text = {
            KnotworkField(label = stringResource(R.string.orchestrator_preset_save_name_label)) {
                KnotworkTextField(value = name, onValueChange = { name = it })
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Builds the default filename suggested to the SAF picker — uses the
 * preset name, replacing characters that are not legal in most filesystems.
 */
private fun defaultFileName(preset: PipelinePreset): String {
    val slug = preset.name
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "preset" }
    return "$slug.preset.json"
}

/** Stable test-tag for the manager root. */
internal const val MANAGER_ROOT_TEST_TAG = "pipeline_presets_manager"

/** Stable test-tag for a manager row (per preset id). */
internal fun managerRowTestTag(id: String): String = "manager_row_$id"

/** Stable test-tag for the per-row overflow icon. */
internal fun managerOverflowTestTag(id: String): String = "manager_overflow_$id"

private const val EXPORT_MIME_TYPE = "application/json"
private const val EXPORT_FAILED_FALLBACK = "Export failed"
private const val EXPORT_SUCCESS_FALLBACK = "Preset exported"
