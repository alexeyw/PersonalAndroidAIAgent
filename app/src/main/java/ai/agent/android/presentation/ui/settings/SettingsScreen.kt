package ai.agent.android.presentation.ui.settings

import ai.agent.android.BuildConfig
import ai.agent.android.R
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.ActiveModelMeta
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.domain.models.ProviderId
import ai.agent.android.domain.models.ToolApprovalPolicy
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.screens.settings.ApproveToolCallsOption
import app.knotwork.design.screens.settings.DestructiveActionKind
import app.knotwork.design.screens.settings.DestructiveActionState
import app.knotwork.design.screens.settings.ExternalProvidersCardState
import app.knotwork.design.screens.settings.IdentityCardState
import app.knotwork.design.screens.settings.LlmParameterSlider
import app.knotwork.design.screens.settings.LlmParametersCardState
import app.knotwork.design.screens.settings.LocalModelCardState
import app.knotwork.design.screens.settings.MemoryCardState
import app.knotwork.design.screens.settings.MemoryStatCell
import app.knotwork.design.screens.settings.NotificationsCardState
import app.knotwork.design.screens.settings.PrivacyCardState
import app.knotwork.design.screens.settings.ProviderRowState
import app.knotwork.design.screens.settings.RestrictionsCardState
import app.knotwork.design.screens.settings.SettingsCallbacks
import app.knotwork.design.screens.settings.SettingsContent
import app.knotwork.design.screens.settings.SettingsViewState
import app.knotwork.design.screens.settings.SettingsVisualState
import app.knotwork.design.screens.settings.SystemInstructionsCardState
import com.jakewharton.processphoenix.ProcessPhoenix
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Phase 22 / Task 9 — redesigned Settings surface.
 *
 * Slim mapper between [SettingsViewModel] state and the catalog's
 * [SettingsContent]. Hosts the SAF launcher for memory export and the
 * `ProcessPhoenix.triggerRebirth` call wired to the restart-required
 * banner.
 *
 * @param viewModel Hilt-injected VM holding the persisted state.
 * @param onBack invoked when the user taps the system back button.
 * @param onOpenModels invoked from the Local model card's "Manage" /
 *   "Change" buttons; routes to the Models screen.
 * @param onOpenProvider invoked when the user taps a collapsed provider
 *   nav-row; routes to the standalone provider detail screen.
 * @param onOpenAddProvider invoked from the "+ Add provider" action.
 * @param onOpenSearch invoked from the magnifying-glass action.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenProvider: (ProviderId) -> Unit = {},
    onOpenAddProvider: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream != null) viewModel.exportMemoryBase(stream)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.snackbarShown()
    }

    val exportFilename = stringResource(R.string.settings_memory_export_filename)
    val viewState = buildViewState(uiState, context)
    val callbacks = buildCallbacks(
        viewModel = viewModel,
        onBack = onBack,
        onOpenModels = onOpenModels,
        onOpenAddProvider = onOpenAddProvider,
        onOpenSearch = onOpenSearch,
        onProviderClick = { id ->
            ProviderId.entries.firstOrNull { it.cloudProvider.id == id }?.let(onOpenProvider)
        },
        onExportClick = { exportLauncher.launch(exportFilename) },
        onRestart = {
            viewModel.acknowledgeRestart()
            // No-arg overload — ProcessPhoenix resolves the app's launcher
            // intent via PackageManager. The two-arg form requires an
            // intent that already resolves to a concrete component, and
            // a bare `Intent()` does not, so PhoenixActivity blew up with
            // `ActivityNotFoundException` and the :phoenix process died
            // before it could kill the original (visible to the user as
            // "Restart only quits the app").
            ProcessPhoenix.triggerRebirth(context.applicationContext)
        },
    )

    Box(modifier = modifier.fillMaxSize()) {
        SettingsContent(state = viewState, callbacks = callbacks)
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
@Suppress("LongMethod")
private fun buildViewState(uiState: SettingsUiState, context: android.content.Context): SettingsViewState {
    val buildDate = formatBuildDate(BuildConfig.GIT_COMMIT_DATE_EPOCH_MS)
    val placeholder = stringResource(R.string.settings_field_system_prompt_prefix)
    val helperText = stringResource(R.string.settings_system_instructions_helper)
    val characterCount = uiState.systemInstructions.length
    val charLimit = SettingsDefaults.SYSTEM_INSTRUCTIONS_CHAR_LIMIT
    val tokenApprox = (characterCount / CHARS_PER_TOKEN).toInt().coerceAtLeast(0)
    val identity = uiState.identity?.let { id ->
        IdentityCardState(
            displayName = id.displayName,
            avatarInitials = context.getString(R.string.settings_identity_avatar_initials),
            metaLine = context.getString(
                if (id.keystoreAvailable) {
                    R.string.settings_identity_meta_keystore_ok
                } else {
                    R.string.settings_identity_meta_keystore_missing
                },
                id.deviceId,
            ),
        )
    }
    val systemInstructions = SystemInstructionsCardState(
        value = uiState.systemInstructions,
        placeholder = placeholder,
        variableChips = uiState.variableCatalog.map { it.placeholder },
        characterCount = characterCount,
        characterLimit = charLimit,
        approximateTokens = tokenApprox,
        helperText = helperText,
        validationError = if (characterCount > charLimit) {
            context.getString(R.string.settings_system_instructions_too_long, charLimit)
        } else {
            null
        },
    )
    val restrictions = RestrictionsCardState(
        approveSelection = when (uiState.toolApprovalPolicy) {
            ToolApprovalPolicy.AllCalls -> ApproveToolCallsOption.AllCalls
            ToolApprovalPolicy.SensitiveOrDestructive -> ApproveToolCallsOption.Sensitive
            ToolApprovalPolicy.NeverPrompt -> ApproveToolCallsOption.Never
        },
        approveAllLabel = stringResource(R.string.settings_restrictions_approve_all),
        approveSensitiveLabel = stringResource(R.string.settings_restrictions_approve_sensitive),
        approveNeverLabel = stringResource(R.string.settings_restrictions_approve_never),
        blockDestructive = uiState.blockDestructiveTools,
        blockDestructiveSubtitle = stringResource(R.string.settings_restrictions_block_destructive_subtitle),
        blockNetwork = uiState.blockNetworkFromLocalModel,
        blockNetworkSubtitle = stringResource(R.string.settings_restrictions_block_network_subtitle),
        capSteps = uiState.capAutonomousSteps,
        capStepsSubtitle = stringResource(R.string.settings_restrictions_cap_steps_subtitle),
    )
    val locale = LocalConfiguration.current.locales[0]
    val llm = LlmParametersCardState(
        sliders = listOf(
            LlmParameterSlider(
                id = "temperature",
                title = stringResource(R.string.settings_row_temperature_title),
                valueLabel = String.format(locale, "%.1f", uiState.temperature),
                value = uiState.temperature,
                valueRange = 0f..2f,
            ),
            LlmParameterSlider(
                id = "top_k",
                title = stringResource(R.string.settings_row_top_k_title),
                valueLabel = uiState.topK.toString(),
                value = uiState.topK.toFloat(),
                valueRange = 1f..100f,
                steps = 99,
            ),
            LlmParameterSlider(
                id = "top_p",
                title = stringResource(R.string.settings_row_top_p_title),
                valueLabel = String.format(locale, "%.2f", uiState.topP),
                value = uiState.topP,
                valueRange = 0f..1f,
            ),
            LlmParameterSlider(
                id = "repetition_penalty",
                title = stringResource(R.string.settings_row_repetition_penalty_title),
                valueLabel = String.format(locale, "%.2f", uiState.repetitionPenalty),
                value = uiState.repetitionPenalty,
                valueRange = 1f..2f,
            ),
            LlmParameterSlider(
                id = "max_context",
                title = stringResource(R.string.settings_row_max_context_title),
                valueLabel = "${uiState.maxContextLength} tok",
                value = uiState.maxContextLength.toFloat(),
                valueRange = 512f..8192f,
                steps = 14,
            ),
            LlmParameterSlider(
                id = "max_steps",
                title = stringResource(R.string.settings_row_max_steps_title),
                valueLabel = uiState.capAutonomousSteps.toString(),
                value = uiState.capAutonomousSteps.toFloat(),
                valueRange = 5f..100f,
                steps = 94,
            ),
        ),
    )
    val activeMeta = uiState.activeModelMeta
    val localModel = LocalModelCardState(
        modelName = activeMeta?.name,
        metaLine = activeMeta?.let { formatActiveModelMeta(it, context) },
        backendLabel = stringResource(
            R.string.settings_row_inference_backend_subtitle,
            uiState.localModelBackend,
        ),
        backendOptions = LocalBackend.entries.map { it.key },
        selectedBackend = uiState.localModelBackend,
        testProbeText = formatTestProbe(uiState, context),
        testProbeIsError = uiState.lastTestProbeResult?.success == false,
    )
    val providers = ExternalProvidersCardState(
        rows = uiState.providers.map { summary ->
            ProviderRowState(
                id = summary.id.cloudProvider.id,
                title = summary.displayName,
                fingerprint = summary.keyFingerprint,
                model = summary.model,
                endpointHint = summary.endpointHint,
                isLan = summary.isLanLocal,
            )
        },
    )
    val memory = MemoryCardState(
        stats = listOf(
            MemoryStatCell(
                value = uiState.memoryStats.chunkCount.toString(),
                label = stringResource(R.string.settings_memory_stat_chunks),
            ),
            MemoryStatCell(
                value = formatBytes(uiState.memoryStats.totalBytes),
                label = stringResource(R.string.settings_memory_stat_size),
            ),
            MemoryStatCell(
                value = if (uiState.memoryStats.threadCount > 0) {
                    uiState.memoryStats.threadCount.toString()
                } else {
                    stringResource(R.string.settings_memory_stat_dash)
                },
                label = stringResource(R.string.settings_memory_stat_threads),
            ),
            MemoryStatCell(
                value = uiState.memoryStats.averageSimilarityScore
                    ?.let { String.format(locale, "%.2f", it) }
                    ?: stringResource(R.string.settings_memory_stat_dash),
                label = stringResource(R.string.settings_memory_stat_avg_score),
            ),
        ),
        autoExtractEnabled = uiState.autoExtractEnabled,
        autoExtractLabel = stringResource(R.string.settings_memory_auto_extract_title),
        autoExtractSubtitle = stringResource(R.string.settings_memory_auto_extract_subtitle),
        autoSummarizeThreshold = (uiState.autoSummarizeThreshold * MAX_PERCENT).roundToInt(),
        autoSummarizeLabel = stringResource(R.string.settings_memory_auto_summarize_title),
        embeddingTitle = stringResource(R.string.settings_memory_embedding_title),
        embeddingSubtitle = stringResource(R.string.settings_memory_embedding_subtitle),
        exportLabel = stringResource(R.string.settings_memory_export),
        reembedLabel = stringResource(R.string.settings_memory_reembed),
        clearLabel = stringResource(R.string.settings_memory_clear),
        reembedProgressPercent = uiState.reembedProgress?.let { (it * MAX_PERCENT).roundToInt() },
    )
    val notifications = NotificationsCardState(longRunningEnabled = uiState.longRunningTaskNotificationsEnabled)
    val privacy = PrivacyCardState(
        crashReportingEnabled = uiState.crashReportingEnabled,
        verboseMemoryLoggingEnabled = uiState.verboseMemoryLoggingEnabled,
    )
    val destructive = uiState.pendingDestructive?.let { kind ->
        DestructiveActionState(
            title = stringResource(
                if (kind == PendingDestructiveAction.ClearMemory) {
                    R.string.settings_destructive_clear_memory_title
                } else {
                    R.string.settings_destructive_reset_title
                },
            ),
            body = stringResource(
                if (kind == PendingDestructiveAction.ClearMemory) {
                    R.string.settings_destructive_clear_memory_body
                } else {
                    R.string.settings_destructive_reset_body
                },
            ),
            keyword = stringResource(R.string.settings_destructive_typed_keyword),
            hint = stringResource(R.string.settings_destructive_typed_hint),
            pendingInput = uiState.destructiveTypedInput,
            kind = if (kind == PendingDestructiveAction.ClearMemory) {
                DestructiveActionKind.ClearMemory
            } else {
                DestructiveActionKind.ResetSettings
            },
        )
    }
    val visualState = when {
        uiState.identity == null -> SettingsVisualState.Loading
        uiState.pendingDestructive != null -> SettingsVisualState.DestructiveAction
        uiState.restartRequired -> SettingsVisualState.RestartRequired
        else -> SettingsVisualState.Default
    }
    return SettingsViewState(
        visualState = visualState,
        subtitleVersion = BuildConfig.VERSION_NAME,
        subtitleChannel = BuildConfig.BUILD_TYPE,
        subtitleBuildDate = buildDate,
        identity = identity,
        systemInstructions = systemInstructions,
        restrictions = restrictions,
        llmParameters = llm,
        localModel = localModel,
        externalProviders = providers,
        memory = memory,
        notifications = notifications,
        privacy = privacy,
        restartRequiredMessage = stringResource(R.string.settings_restart_required_message)
            .takeIf { visualState == SettingsVisualState.RestartRequired },
        destructiveAction = destructive,
    )
}

@Composable
@Suppress("LongParameterList")
private fun buildCallbacks(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenAddProvider: () -> Unit,
    onOpenSearch: () -> Unit,
    onProviderClick: (String) -> Unit,
    onExportClick: () -> Unit,
    onRestart: () -> Unit,
): SettingsCallbacks = SettingsCallbacks(
    onBack = onBack,
    onSearchClick = onOpenSearch,
    onSystemInstructionsChange = viewModel::updateSystemInstructions,
    onInsertVariableClick = { /* sheet wiring lands in a follow-up; chip row already inserts. */ },
    onChipInsert = viewModel::insertVariable,
    onApproveSelectionChange = { option ->
        viewModel.setToolApprovalPolicy(
            when (option) {
                ApproveToolCallsOption.AllCalls -> ToolApprovalPolicy.AllCalls
                ApproveToolCallsOption.Sensitive -> ToolApprovalPolicy.SensitiveOrDestructive
                ApproveToolCallsOption.Never -> ToolApprovalPolicy.NeverPrompt
            },
        )
    },
    onBlockDestructiveChange = viewModel::setBlockDestructiveTools,
    onBlockNetworkChange = viewModel::setBlockNetworkFromLocalModel,
    onCapStepsChange = viewModel::setCapAutonomousSteps,
    onSliderChange = { id, value ->
        when (id) {
            "temperature" -> viewModel.setTemperature(value)
            "top_k" -> viewModel.setTopK(value.roundToInt())
            "top_p" -> viewModel.setTopP(value)
            "repetition_penalty" -> viewModel.setRepetitionPenalty(value)
            "max_context" -> viewModel.setMaxContextLength(value.roundToInt())
            "max_steps" -> viewModel.setCapAutonomousSteps(value.roundToInt())
        }
    },
    onResetLlmDefaults = viewModel::resetSamplingDefaults,
    onManageModelsClick = onOpenModels,
    onChangeModelClick = onOpenModels,
    onBackendSelected = viewModel::setLocalModelBackend,
    onTestBackendClick = viewModel::runBackendProbe,
    onProviderRowClick = onProviderClick,
    onAddProviderClick = onOpenAddProvider,
    onAutoExtractToggle = viewModel::setAutoExtractEnabled,
    onAutoSummarizeChange = viewModel::setAutoSummarizeThreshold,
    onExportMemoryClick = onExportClick,
    onReembedClick = viewModel::runReembed,
    onClearMemoryClick = viewModel::stageClearMemory,
    onLongRunningToggle = viewModel::setLongRunningTaskNotificationsEnabled,
    onCrashReportingToggle = viewModel::setCrashReportingEnabled,
    onVerboseMemoryLoggingToggle = viewModel::setVerboseMemoryLoggingEnabled,
    onResetSettingsClick = viewModel::stageResetSettings,
    onRestartClick = onRestart,
    onDestructiveTypedConfirmChange = viewModel::updateDestructiveTypedInput,
    onDestructiveConfirm = viewModel::confirmDestructive,
    onDestructiveCancel = viewModel::cancelDestructive,
)

private fun formatBuildDate(epochMs: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.US).format(java.util.Date(epochMs))

private fun formatActiveModelMeta(meta: ActiveModelMeta, context: android.content.Context): String {
    val size = formatBytes(meta.sizeBytes)
    val ctx = "${meta.contextWindowTokens}"
    val quant = meta.quantization ?: "-"
    val downloaded = meta.downloadedAtMs?.let {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(java.util.Date(it))
    } ?: "-"
    return context.getString(R.string.settings_local_model_meta, size, ctx, quant, downloaded)
}

private fun formatTestProbe(uiState: SettingsUiState, context: android.content.Context): String {
    val probe = uiState.lastTestProbeResult ?: return context.getString(R.string.settings_row_test_backend_idle)
    return if (probe.success) {
        val durationLabel = String.format(Locale.getDefault(), "%.2fs", probe.durationMs / MS_PER_SECOND_F)
        val tpsLabel = String.format(Locale.getDefault(), "%.1f", probe.tokensPerSecond)
        context.getString(R.string.settings_row_test_backend_success, probe.tokensGenerated, durationLabel, tpsLabel)
    } else {
        context.getString(R.string.settings_row_test_backend_failed, probe.errorMessage.orEmpty())
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"
    val kb = bytes / BYTES_PER_KB_F
    if (kb < BYTES_PER_KB) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / BYTES_PER_KB
    if (mb < BYTES_PER_KB) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / BYTES_PER_KB
    return String.format(Locale.getDefault(), "%.1f GB", gb)
}

private const val MIME_JSON = "application/json"
private const val CHARS_PER_TOKEN = 3.5f
private const val MS_PER_SECOND_F = 1_000f
private const val MAX_PERCENT = 100
private const val BYTES_PER_KB = 1_024L
private const val BYTES_PER_KB_F = 1_024f
