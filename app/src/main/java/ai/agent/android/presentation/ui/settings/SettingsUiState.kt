package ai.agent.android.presentation.ui.settings

import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.ActiveModelMeta
import ai.agent.android.domain.models.Identity
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.domain.models.MemoryStats
import ai.agent.android.domain.models.ProviderSummary
import ai.agent.android.domain.models.TestProbeResult
import ai.agent.android.domain.models.ToolApprovalPolicy

/**
 * Top-level Settings screen UI state. Aggregates every slice the
 * redesigned screen needs to render — kept as a single immutable data
 * class so `viewModel.uiState.collectAsState()` returns a snapshot
 * suitable for `derivedStateOf` skips.
 *
 * @property identity Current identity snapshot; `null` while the first
 *   read is in flight.
 * @property systemInstructions Live textarea content (user-editable).
 * @property variableCatalog Catalog of `$VARIABLE` placeholders surfaced
 *   in the chip row beneath the textarea.
 * @property toolApprovalPolicy Currently selected HITL policy.
 * @property blockDestructiveTools Mirror of the persisted toggle.
 * @property blockNetworkFromLocalModel Mirror of the persisted toggle.
 * @property capAutonomousSteps Renamed `pipelineMaxSteps`; trailing value
 *   in the restrictions card.
 * @property temperature / [topK] / [topP] / [repetitionPenalty] /
 *   [maxContextLength] Sampling parameters mirrored from DataStore.
 * @property activeModelMeta Live snapshot of the active model card.
 * @property localModelBackend Wire key of the selected backend
 *   ([LocalBackend.key]).
 * @property lastTestProbeResult Most recent persisted probe outcome.
 * @property providers Collapsed external-provider rows.
 * @property memoryStats Live aggregate counters.
 * @property autoSummarizeThreshold Fraction (0..1) for the threshold
 *   slider.
 * @property reembedProgress `null` when no re-embed job is in flight;
 *   otherwise `0f..1f`.
 * @property longRunningTaskNotificationsEnabled Mirror of the toggle.
 * @property crashReportingEnabled Mirror of the toggle.
 * @property restartRequired `true` after the user changed an
 *   inference-backend / Ollama base URL that requires a process restart.
 * @property pendingDestructive Currently staged destructive action,
 *   `null` when no dialog is active.
 * @property destructiveTypedInput Live text in the typed-confirm field.
 * @property snackbarMessage One-shot message surfaced via the screen-level
 *   SnackbarHost; consumed by [SettingsViewModel.snackbarShown].
 */
data class SettingsUiState(
    val identity: Identity? = null,
    val systemInstructions: String = "",
    val variableCatalog: List<VariableCatalogChip> = emptyList(),
    val toolApprovalPolicy: ToolApprovalPolicy = ToolApprovalPolicy.DEFAULT,
    val blockDestructiveTools: Boolean = false,
    val blockNetworkFromLocalModel: Boolean = false,
    val capAutonomousSteps: Int = SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT,
    val temperature: Float = SettingsDefaults.TEMPERATURE_DEFAULT,
    val topK: Int = SettingsDefaults.TOP_K_DEFAULT,
    val topP: Float = SettingsDefaults.TOP_P_DEFAULT,
    val repetitionPenalty: Float = SettingsDefaults.REPETITION_PENALTY_DEFAULT,
    val maxContextLength: Int = SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT,
    val activeModelMeta: ActiveModelMeta? = null,
    val localModelBackend: String = LocalBackend.CPU.key,
    val lastTestProbeResult: TestProbeResult? = null,
    val testProbeInFlight: Boolean = false,
    val providers: List<ProviderSummary> = emptyList(),
    val memoryStats: MemoryStats = MemoryStats.EMPTY,
    val autoSummarizeThreshold: Float = SettingsDefaults.AUTO_SUMMARIZE_THRESHOLD_DEFAULT,
    val reembedProgress: Float? = null,
    val longRunningTaskNotificationsEnabled: Boolean = true,
    val crashReportingEnabled: Boolean = false,
    val restartRequired: Boolean = false,
    val pendingDestructive: PendingDestructiveAction? = null,
    val destructiveTypedInput: String = "",
    val snackbarMessage: String? = null,
)

/**
 * One `$VARIABLE` chip in the System instructions card.
 *
 * @property placeholder Raw placeholder (with leading `$`).
 * @property sample Live resolved value used as a tooltip / chip subtitle.
 */
data class VariableCatalogChip(val placeholder: String, val sample: String)

/**
 * Destructive action currently staged behind the typed-confirm dialog.
 */
enum class PendingDestructiveAction { ClearMemory, ResetSettings }
