package app.knotwork.android.presentation.ui.orchestrator.presets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.knotwork.android.R
import app.knotwork.android.domain.models.PipelinePreset
import app.knotwork.android.domain.models.PresetCategory
import app.knotwork.android.presentation.ui.common.asString
import app.knotwork.design.components.chips.KnotworkChipSize
import app.knotwork.design.components.chips.KnotworkFilterChip
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.components.topbar.KnotworkTopAppBarShell
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import kotlinx.coroutines.launch

/**
 * Full-screen manager for bundled and user-saved pipeline presets.
 *
 * Layout:
 *
 *  - `KnotworkTopAppBarShell` with the back arrow, "Pipeline presets" title,
 *    and a mono-spaced "N bundled · M saved" subtitle.
 *  - `PrimaryTabRow` with Bundled / Mine tabs labelled `Bundled · N`.
 *  - Horizontal `KnotworkFilterChip` row for category filters with built-in
 *    trailing count badges (`All · 6`, `Local · 2`, …); only categories that
 *    actually contain rows under the active tab are surfaced.
 *  - `LazyColumn` of preset cards — name + colored category badge + 2-line
 *    description + mono graph-flow preview. Trailing `⋮` IconButton opens a
 *    DropdownMenu with Rename / Export JSON / Delete (Rename and Delete are
 *    only listed for user-owned presets).
 *
 * The screen uses `contentWindowInsets = WindowInsets(0, 0, 0, 0)` so the
 * Scaffold does not add bottom-nav padding to the body — this matches the
 * convention used in `ChatHomeContent` and prevents the visible gap that
 * appeared above the bottom navigation bar in the first revision.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
        // Suppress the default body insets so the bottom-nav bar doesn't
        // leave a visible gap above the row list. Matches the convention
        // used by ChatHomeContent (the only other multi-tab body that sits
        // directly above the bottom nav).
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            KnotworkTopAppBarShell {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.orchestrator_preset_manager_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(
                                    R.string.orchestrator_preset_manager_subtitle,
                                    state.bundledPresets.size,
                                    state.userPresets.size,
                                ),
                                style = KnotworkTextStyles.MonoSm,
                                color = KnotworkTheme.extended.onSurfaceMuted,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.Back, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PresetTabRow(
                activeTab = state.activeTab,
                bundledCount = state.bundledPresets.size,
                userCount = state.userPresets.size,
                onTabSelected = viewModel::selectTab,
            )

            PresetCategoryChipRow(
                visibleCategories = state.visibleCategories,
                presets = state.presetsForActiveTab,
                selectedCategory = state.selectedCategory,
                onCategorySelected = viewModel::selectCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = KnotworkTheme.spacing.sp4,
                        vertical = KnotworkTheme.spacing.sp3,
                    ),
            )

            HorizontalDivider(color = KnotworkTheme.extended.divider)

            val visible = state.filteredPresets
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(KnotworkTheme.spacing.sp6),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.activeTab == PresetPickerTab.Bundled) {
                            stringResource(R.string.orchestrator_preset_manager_bundled_empty)
                        } else {
                            stringResource(R.string.orchestrator_preset_manager_user_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp1),
                ) {
                    items(items = visible, key = { it.id }) { preset ->
                        PresetManagerRow(
                            preset = preset,
                            onRename = if (preset.isBundled) null else { -> renameTarget = preset },
                            onDelete = if (preset.isBundled) null else { -> deleteTarget = preset },
                            onExport = {
                                pendingExportPreset = preset
                                exportLauncher.launch(defaultFileName(preset))
                            },
                        )
                        HorizontalDivider(color = KnotworkTheme.extended.divider)
                    }
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

/**
 * Bundled / Mine `PrimaryTabRow` with built-in count badges. Reused by both
 * the manager screen and the [PresetPickerSheet] so the chrome is identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetTabRow(
    activeTab: PresetPickerTab,
    bundledCount: Int,
    userCount: Int,
    onTabSelected: (PresetPickerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = activeTab.ordinal,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        PresetPickerTab.entries.forEach { tab ->
            val count = if (tab == PresetPickerTab.Bundled) bundledCount else userCount
            Tab(
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) },
                text = { PresetTabLabel(tab = tab, count = count, selected = activeTab == tab) },
                modifier = Modifier.testTag(tag = presetTabTestTag(tab)),
            )
        }
    }
}

/**
 * Tab label "Bundled · N". The count is appended with a middle-dot so the
 * tab keeps a single text-baseline (a separate badge atom would shift the
 * label vertical centre and break the underline indicator alignment).
 */
@Composable
private fun PresetTabLabel(tab: PresetPickerTab, count: Int, selected: Boolean) {
    val label = when (tab) {
        PresetPickerTab.Bundled -> stringResource(R.string.orchestrator_preset_picker_tab_bundled)
        PresetPickerTab.Mine -> stringResource(R.string.orchestrator_preset_picker_tab_mine)
    }
    Text(
        text = "$label · $count",
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.onSurfaceMuted,
    )
}

/**
 * Scrollable chip row exposing the "All" reset chip + every category present
 * under the active tab. Each chip carries a count badge ("All · 6").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetCategoryChipRow(
    visibleCategories: List<PresetCategory>,
    presets: List<PipelinePreset>,
    selectedCategory: PresetCategory?,
    onCategorySelected: (PresetCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = modifier,
    ) {
        KnotworkFilterChip(
            label = stringResource(R.string.orchestrator_preset_picker_chip_all),
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            size = KnotworkChipSize.Sm,
            trailingCount = presets.size,
        )
        visibleCategories.forEach { category ->
            val count = presets.count { it.category == category }
            KnotworkFilterChip(
                label = presetCategoryLabelText(category),
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                size = KnotworkChipSize.Sm,
                trailingCount = count,
            )
        }
    }
}

/**
 * One preset row in the manager. Bundled rows pass `null` for [onRename] and
 * [onDelete] so the overflow only surfaces the Export action.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PresetManagerRow(
    preset: PipelinePreset,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            )
            .testTag(tag = managerRowTestTag(preset.id)),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PresetCategoryBadge(category = preset.category)
            }
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = GraphFlowPreview.render(preset.graph),
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.testTag(tag = managerOverflowTestTag(preset.id)),
            ) {
                Icon(AppIcons.More, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onRename != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.orchestrator_preset_manager_action_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(AppIcons.Download, contentDescription = null) },
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
                                imageVector = AppIcons.Trash,
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
 * Builds the default filename suggested to the SAF picker — uses the preset
 * name, replacing characters that are not legal in most filesystems.
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
