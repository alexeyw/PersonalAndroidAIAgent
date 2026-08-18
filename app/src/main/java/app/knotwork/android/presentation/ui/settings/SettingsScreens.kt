@file:Suppress("MatchingDeclarationName")

package app.knotwork.android.presentation.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.android.R
import app.knotwork.android.domain.models.EntrySurface
import app.knotwork.android.domain.models.MemoryImportStrategy
import app.knotwork.android.domain.models.ProviderId
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.presentation.tile.requestAddDutyTile
import app.knotwork.design.screens.settings.AboutSettingsContent
import app.knotwork.design.screens.settings.ApproveToolCallsOption
import app.knotwork.design.screens.settings.BackgroundSettingsContent
import app.knotwork.design.screens.settings.GenerationSettingsContent
import app.knotwork.design.screens.settings.HubSearchResultRow
import app.knotwork.design.screens.settings.LocalSettingsHighlightKey
import app.knotwork.design.screens.settings.MemorySettingsContent
import app.knotwork.design.screens.settings.ModelsSettingsContent
import app.knotwork.design.screens.settings.PipelinesSettingsContent
import app.knotwork.design.screens.settings.PrivacySettingsContent
import app.knotwork.design.screens.settings.SLIDER_AUDIO_MAX_DURATION
import app.knotwork.design.screens.settings.SLIDER_BACKGROUND_APPROVAL_WINDOW
import app.knotwork.design.screens.settings.SLIDER_BACKGROUND_RESUME_MAX_AGE
import app.knotwork.design.screens.settings.SLIDER_MAX_CONTEXT
import app.knotwork.design.screens.settings.SLIDER_MEMORY_AUTO_SUMMARIZE
import app.knotwork.design.screens.settings.SLIDER_MEMORY_COMPACTION_AGE
import app.knotwork.design.screens.settings.SLIDER_MEMORY_COMPRESSION_THRESHOLD
import app.knotwork.design.screens.settings.SLIDER_MEMORY_LIVE_WINDOW
import app.knotwork.design.screens.settings.SLIDER_MEMORY_MAX_CHUNKS
import app.knotwork.design.screens.settings.SLIDER_MEMORY_RECENCY_HALF_LIFE
import app.knotwork.design.screens.settings.SLIDER_MEMORY_SEARCH_THRESHOLD
import app.knotwork.design.screens.settings.SLIDER_MEMORY_SEARCH_TOP_K
import app.knotwork.design.screens.settings.SLIDER_MEMORY_SUMMARY_LIMIT
import app.knotwork.design.screens.settings.SLIDER_PIPELINE_CAP_STEPS
import app.knotwork.design.screens.settings.SLIDER_PIPELINE_NESTING_DEPTH
import app.knotwork.design.screens.settings.SLIDER_PIPELINE_STRUCTURED_REPAIRS
import app.knotwork.design.screens.settings.SLIDER_PRIVACY_RETENTION_AGE
import app.knotwork.design.screens.settings.SLIDER_PRIVACY_RETENTION_RUNS
import app.knotwork.design.screens.settings.SLIDER_REPETITION_PENALTY
import app.knotwork.design.screens.settings.SLIDER_TEMPERATURE
import app.knotwork.design.screens.settings.SLIDER_TOP_K
import app.knotwork.design.screens.settings.SLIDER_TOP_P
import app.knotwork.design.screens.settings.SettingsCallbacks
import app.knotwork.design.screens.settings.SettingsCategoryId
import app.knotwork.design.screens.settings.SettingsHubContent
import app.knotwork.design.screens.settings.ToolsSettingsContent
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import app.knotwork.android.domain.settings.SettingsCategoryId as DomainCategoryId

/**
 * Navigation actions threaded into every settings screen from the nav graph.
 * Kept as one bundle so each screen takes a single `nav` argument.
 *
 * @property onBack Up navigation from the current screen.
 * @property onOpenCategory Open a category sub-screen from the hub.
 * @property onOpenModels Open the model-discovery / management surface.
 * @property onOpenProvider Open a cloud-provider detail screen.
 * @property onOpenAddProvider Open the add-provider picker.
 * @property onOpenManageTools Open the Tools / MCP screen.
 * @property onOpenAllowedDomains Open the allowed-HTTP-domains screen.
 * @property onOpenLicenses Open the About / licenses screen.
 * @property onOpenUsageStatistics Open the on-device Usage statistics screen.
 */
data class SettingsNavActions(
    val onBack: () -> Unit,
    val onOpenCategory: (SettingsCategoryId) -> Unit,
    val onOpenModels: () -> Unit,
    val onOpenProvider: (ProviderId) -> Unit,
    val onOpenAddProvider: () -> Unit,
    val onOpenManageTools: () -> Unit,
    val onOpenAllowedDomains: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenUsageStatistics: () -> Unit,
)

/** Settings hub: search field, the six inline Basic controls and the category list. */
@Composable
fun SettingsHubScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = rememberSettingsCallbacks(
        viewModel = viewModel,
        nav = nav,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onSearchResultClick = { row ->
            viewModel.requestHighlight(row.anchorKey)
            nav.onOpenCategory(row.categoryId)
        },
    )
    SettingsSurface(viewModel) {
        SettingsHubContent(state = buildHubViewState(uiState), callbacks = callbacks)
    }
}

/**
 * Reads the ViewModel-resolved highlight for [category] and, when one targets
 * this screen, schedules it to clear after a short dwell so the flash fires once
 * on arrival. The registry lookup itself lives in the ViewModel
 * ([SettingsViewModel.resolveHighlight]); this only owns the consume effect.
 */
@Composable
private fun rememberCategoryHighlight(
    viewModel: SettingsViewModel,
    anchor: String?,
    category: DomainCategoryId,
): SettingsHighlight {
    val highlight = viewModel.resolveHighlight(anchor, category)
    LaunchedEffect(highlight.key) {
        if (highlight.key != null) {
            delay(HIGHLIGHT_CONSUME_MS)
            viewModel.highlightConsumed()
        }
    }
    return highlight
}

/** Generation category sub-screen. */
@Composable
fun GenerationSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.GENERATION)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            GenerationSettingsContent(
                state = buildGenerationViewState(uiState, context),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
}

/** Models category sub-screen. */
@Composable
fun ModelsSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.MODELS)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            ModelsSettingsContent(state = buildModelsViewState(uiState, context), callbacks = callbacks)
        }
    }
}

/** Memory category sub-screen (also hosts the import strategy dialog). */
@Composable
fun MemorySettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.MEMORY)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            MemorySettingsContent(
                state = buildMemoryViewState(uiState, context),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
        uiState.pendingImport?.let { pending ->
            MemoryImportDialog(
                pending = pending,
                onMerge = { viewModel.confirmImport(MemoryImportStrategy.Merge) },
                onReplace = { viewModel.confirmImport(MemoryImportStrategy.Replace) },
                onCancel = viewModel::cancelImport,
            )
        }
    }
}

/** Pipelines-&-structured-output category sub-screen. */
@Composable
fun PipelinesSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.PIPELINES)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            PipelinesSettingsContent(
                state = buildPipelinesViewState(uiState),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
}

/** Tools-&-workspace category sub-screen. */
@Composable
fun ToolsSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.TOOLS)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            ToolsSettingsContent(
                state = buildToolsViewState(uiState),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
}

/** Background-&-triggers category sub-screen. */
@Composable
fun BackgroundSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pickerSurface by remember { mutableStateOf<EntrySurface?>(null) }
    val callbacks = rememberSettingsCallbacks(
        viewModel = viewModel,
        nav = nav,
        onShareTargetPipelineClick = { pickerSurface = EntrySurface.SHARE },
        onQuickTilePipelineClick = { pickerSurface = EntrySurface.QUICK_TILE },
    )
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.BACKGROUND)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            BackgroundSettingsContent(
                state = buildBackgroundViewState(uiState),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
    pickerSurface?.let { surface ->
        // Exhaustive on purpose: a new EntrySurface must not compile until this
        // screen decides what it shows for it. A `null` title marks a surface
        // bound elsewhere, and the picker is then simply not rendered rather
        // than titled with a sentence written about a different surface.
        val titleRes = when (surface) {
            EntrySurface.SHARE -> R.string.settings_pipeline_picker_share_title
            EntrySurface.QUICK_TILE -> R.string.settings_pipeline_picker_tile_title
            EntrySurface.EXTERNAL_AUTOMATION -> null
        } ?: return@let
        val selectedId = when (surface) {
            EntrySurface.SHARE -> uiState.shareTargetPipelineId
            EntrySurface.QUICK_TILE -> uiState.quickSettingsTilePipelineId
            EntrySurface.EXTERNAL_AUTOMATION -> null
        }
        SurfacePipelinePickerDialog(
            title = stringResource(titleRes),
            options = uiState.bindablePipelines,
            selectedId = selectedId,
            onSelect = { pipelineId ->
                viewModel.setSurfacePipeline(surface, pipelineId)
                // Offer to pin the tile in context, once the user has bound it.
                if (surface == EntrySurface.QUICK_TILE && pipelineId != null) requestAddDutyTile(context)
                pickerSurface = null
            },
            onDismiss = { pickerSurface = null },
        )
    }
}

/**
 * Single-choice picker binding a pipeline to an entry surface. Reuses a plain
 * Material 3 [AlertDialog] with a radio list (the "Reset settings" dialog idiom)
 * rather than introducing a new design-system component; "None" clears the
 * binding so the surface returns to its inert privacy-first default.
 */
@Composable
private fun SurfacePipelinePickerDialog(
    title: String,
    options: List<PipelineBindingOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PipelinePickerRow(
                    label = stringResource(R.string.settings_pipeline_picker_none),
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
                options.forEach { option ->
                    PipelinePickerRow(
                        label = option.name,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** One selectable radio row inside [SurfacePipelinePickerDialog]. */
@Composable
private fun PipelinePickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Privacy category sub-screen. */
@Composable
fun PrivacySettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.PRIVACY)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            PrivacySettingsContent(
                state = buildPrivacyViewState(uiState),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
}

/** About category sub-screen (also hosts the reset confirm dialog via the content). */
@Composable
fun AboutSettingsScreen(viewModel: SettingsViewModel, nav: SettingsNavActions) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val callbacks = rememberSettingsCallbacks(viewModel, nav)
    val highlight = rememberCategoryHighlight(viewModel, uiState.pendingHighlightAnchor, DomainCategoryId.ABOUT)
    SettingsSurface(viewModel) {
        CompositionLocalProvider(LocalSettingsHighlightKey provides highlight.key) {
            AboutSettingsContent(
                state = buildAboutViewState(uiState, context),
                callbacks = callbacks,
                advancedExpanded = highlight.advancedExpanded,
            )
        }
    }
}

/**
 * Wraps a settings screen body in a Box that hosts the one-shot snackbar surfaced
 * by VM actions (memory export, reset, probe, sampling reset).
 */
@Composable
private fun SettingsSurface(viewModel: SettingsViewModel, content: @Composable () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.snackbarShown()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        SnackbarHost(hostState = snackbarHostState)
    }
}

/**
 * Builds the unified [SettingsCallbacks] bag shared by every settings screen:
 * VM forwarders, the nav actions, the memory SAF launchers and the restart
 * trigger. Every screen receives the full bag and uses only the subset its
 * controls invoke.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun rememberSettingsCallbacks(
    viewModel: SettingsViewModel,
    nav: SettingsNavActions,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchResultClick: (HubSearchResultRow) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onShareTargetPipelineClick: () -> Unit = {},
    onQuickTilePipelineClick: () -> Unit = {},
): SettingsCallbacks {
    val context = LocalContext.current
    val exportFilename = stringResource(R.string.settings_memory_export_filename)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream != null) viewModel.exportMemoryBase(stream)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream != null) viewModel.importMemory(stream)
    }
    return SettingsCallbacks(
        onBack = nav.onBack,
        onOpenCategory = nav.onOpenCategory,
        onSearchQueryChange = onSearchQueryChange,
        onSearchResultClick = onSearchResultClick,
        onClearSearch = onClearSearch,
        onOpenSystemInstructions = { nav.onOpenCategory(SettingsCategoryId.Generation) },
        onManageModelsClick = nav.onOpenModels,
        onOpenManageTools = nav.onOpenManageTools,
        onOpenAllowedDomains = nav.onOpenAllowedDomains,
        onOpenLicenses = nav.onOpenLicenses,
        onSystemInstructionsChange = viewModel::updateSystemInstructions,
        onChipInsert = viewModel::insertVariable,
        onToolUsageInstructionChange = viewModel::updateToolUsageInstruction,
        onGenerationSliderChange = { id, value -> routeGenerationSlider(viewModel, id, value) },
        onResetSamplingDefaults = viewModel::resetSamplingDefaults,
        onApproveSelectionChange = { option -> viewModel.setToolApprovalPolicy(option.toPolicy()) },
        onBlockDestructiveChange = viewModel::setBlockDestructiveTools,
        onBlockNetworkChange = viewModel::setBlockNetworkFromLocalModel,
        onPipelinesSliderChange = { id, value -> routePipelinesSlider(viewModel, id, value) },
        onBackendSelected = viewModel::setLocalModelBackend,
        onTestBackendClick = viewModel::runBackendProbe,
        onProviderRowClick = { id ->
            ProviderId.entries.firstOrNull { it.cloudProvider.id == id }?.let(nav.onOpenProvider)
        },
        onAddProviderClick = nav.onOpenAddProvider,
        onRestartClick = {
            viewModel.acknowledgeRestart()
            ProcessPhoenix.triggerRebirth(context.applicationContext)
        },
        onAutoExtractToggle = viewModel::setAutoExtractEnabled,
        onMemoryCompactionToggle = viewModel::setMemoryCompactionEnabled,
        onChatHistoryCompressionToggle = viewModel::setChatHistoryCompressionEnabled,
        onMemorySliderChange = { id, value -> routeMemorySlider(viewModel, id, value) },
        onVerboseMemoryLoggingToggle = viewModel::setVerboseMemoryLoggingEnabled,
        onEmbeddingProviderSelected = viewModel::setActiveEmbeddingProviderId,
        onExportMemoryClick = { exportLauncher.launch(exportFilename) },
        onImportMemoryClick = { importLauncher.launch(arrayOf(MIME_JSON)) },
        onReembedClick = viewModel::runReembed,
        onClearMemoryClick = viewModel::stageClearMemory,
        onLongRunningToggle = viewModel::setLongRunningTaskNotificationsEnabled,
        onScheduledResultsToggle = viewModel::setScheduledTaskNotificationsEnabled,
        onBackgroundSliderChange = { id, value -> routeBackgroundSlider(viewModel, id, value) },
        onShareTargetPipelineClick = onShareTargetPipelineClick,
        onShareReuseSessionToggle = viewModel::setShareReuseSession,
        onQuickTilePipelineClick = onQuickTilePipelineClick,
        onCrashReportingToggle = viewModel::setCrashReportingEnabled,
        onPrivacySliderChange = { id, value -> routePrivacySlider(viewModel, id, value) },
        onOpenUsageStatistics = nav.onOpenUsageStatistics,
        onResetSettingsClick = viewModel::stageResetSettings,
        onDestructiveTypedConfirmChange = viewModel::updateDestructiveTypedInput,
        onDestructiveConfirm = viewModel::confirmDestructive,
        onDestructiveCancel = viewModel::cancelDestructive,
    )
}

private fun routeGenerationSlider(viewModel: SettingsViewModel, id: String, value: Float) {
    when (id) {
        SLIDER_TEMPERATURE -> viewModel.setTemperature(value)
        SLIDER_TOP_K -> viewModel.setTopK(value.roundToInt())
        SLIDER_TOP_P -> viewModel.setTopP(value)
        SLIDER_REPETITION_PENALTY -> viewModel.setRepetitionPenalty(value)
        SLIDER_MAX_CONTEXT -> viewModel.setMaxContextLength(value.roundToInt())
        SLIDER_AUDIO_MAX_DURATION -> viewModel.setAudioMaxDurationSec(value.roundToInt())
    }
}

private fun routePipelinesSlider(viewModel: SettingsViewModel, id: String, value: Float) {
    when (id) {
        SLIDER_PIPELINE_CAP_STEPS -> viewModel.setCapAutonomousSteps(value.roundToInt())
        SLIDER_PIPELINE_NESTING_DEPTH -> viewModel.setPipelineMaxNestingDepth(value.roundToInt())
        SLIDER_PIPELINE_STRUCTURED_REPAIRS -> viewModel.setStructuredOutputMaxRepairs(value.roundToInt())
    }
}

private fun routeMemorySlider(viewModel: SettingsViewModel, id: String, value: Float) {
    when (id) {
        SLIDER_MEMORY_AUTO_SUMMARIZE -> viewModel.setAutoSummarizeThreshold(value.roundToInt())
        SLIDER_MEMORY_SEARCH_TOP_K -> viewModel.setMemorySearchTopK(value.roundToInt())
        SLIDER_MEMORY_SEARCH_THRESHOLD -> viewModel.setMemorySearchThreshold(value)
        SLIDER_MEMORY_RECENCY_HALF_LIFE -> viewModel.setMemoryRecencyHalfLifeDays(value.roundToInt())
        SLIDER_MEMORY_COMPACTION_AGE -> viewModel.setMemoryCompactionAgeDays(value.roundToInt())
        SLIDER_MEMORY_MAX_CHUNKS -> viewModel.setMaxMemoryChunks(value.roundToInt())
        SLIDER_MEMORY_COMPRESSION_THRESHOLD -> viewModel.setChatHistoryCompressionThresholdTokens(value.roundToInt())
        SLIDER_MEMORY_LIVE_WINDOW -> viewModel.setChatHistoryLiveWindowSize(value.roundToInt())
        SLIDER_MEMORY_SUMMARY_LIMIT -> viewModel.setMemorySummaryDefaultLimit(value.roundToInt())
    }
}

private fun routeBackgroundSlider(viewModel: SettingsViewModel, id: String, value: Float) {
    when (id) {
        SLIDER_BACKGROUND_RESUME_MAX_AGE -> viewModel.setResumeMaxAgeHours(value.roundToInt())
        SLIDER_BACKGROUND_APPROVAL_WINDOW -> viewModel.setBackgroundApprovalWindowHours(value.roundToInt())
    }
}

private fun routePrivacySlider(viewModel: SettingsViewModel, id: String, value: Float) {
    when (id) {
        SLIDER_PRIVACY_RETENTION_RUNS -> viewModel.setTraceRetentionRunsPerSession(value.roundToInt())
        SLIDER_PRIVACY_RETENTION_AGE -> viewModel.setTraceRetentionMaxAgeDays(value.roundToInt())
    }
}

private fun ApproveToolCallsOption.toPolicy(): ToolApprovalPolicy = when (this) {
    ApproveToolCallsOption.AllCalls -> ToolApprovalPolicy.AllCalls
    ApproveToolCallsOption.Sensitive -> ToolApprovalPolicy.SensitiveOrDestructive
    ApproveToolCallsOption.Never -> ToolApprovalPolicy.NeverPrompt
}

/**
 * Strategy-choice dialog raised after a memory import file parses. Lets the user
 * pick Merge (keep existing, skip duplicate ids) or Replace (wipe then load), and
 * surfaces provider / schema mismatch warnings.
 */
@Composable
private fun MemoryImportDialog(
    pending: PendingMemoryImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val warnings = buildList {
        if (pending.schemaMismatch) add(stringResource(R.string.settings_memory_import_schema_warning))
        if (pending.providerMismatch) {
            add(stringResource(R.string.settings_memory_import_provider_warning, pending.document.embeddingProviderId))
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_memory_import_dialog_title)) },
        text = {
            Text(
                buildString {
                    append(stringResource(R.string.settings_memory_import_dialog_body, pending.document.chunks.size))
                    warnings.forEach { warning ->
                        append("\n\n")
                        append(warning)
                    }
                },
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(IMPORT_BUTTON_GAP)) {
                TextButton(onClick = onMerge) { Text(stringResource(R.string.settings_memory_import_merge)) }
                TextButton(
                    onClick = onReplace,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_memory_import_replace))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_memory_import_cancel)) }
        },
    )
}

private const val MIME_JSON = "application/json"
private val IMPORT_BUTTON_GAP = 8.dp

/** Dwell before a settings-search deep-link highlight clears (covers the flash). */
private const val HIGHLIGHT_CONSUME_MS = 1500L
