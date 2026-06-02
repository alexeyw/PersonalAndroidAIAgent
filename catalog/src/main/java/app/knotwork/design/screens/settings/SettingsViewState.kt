package app.knotwork.design.screens.settings

/**
 * Visual variant of the settings surface. Mirrors
 * `compose/screens/README.md §C7 · Settings` with two additions kept
 * orthogonal to the section state (Search and Saving) because they
 * apply globally regardless of which card is open.
 */
enum class SettingsVisualState {
    /** Initial DataStore + repository read in flight. */
    Loading,

    /** Standard surface; cards render their controls. */
    Default,

    /** Search sheet is open over the body. */
    Search,

    /** Some change requires an app restart to take effect. */
    RestartRequired,

    /** Destructive-action typed-confirm dialog is up. */
    DestructiveAction,

    /** Failed to load a remote setting; an inline error tile sits inside the affected card. */
    Error,
}

/**
 * Approve-tool-calls segmented control state — mirrors
 * [ai.agent.android.domain.models.ToolApprovalPolicy] but stays free of
 * domain imports so the catalog module keeps its zero-app dependency.
 */
enum class ApproveToolCallsOption {
    AllCalls,
    Sensitive,
    Never,
}

/**
 * Identity card slice. `null` while the first identity read is in flight
 * — the card then renders a short skeleton.
 *
 * @property displayName Localized "Anonymous · this device" label.
 * @property avatarInitials Two-letter avatar fallback (e.g. "AA").
 * @property metaLine Secondary line ("device-id 4f3a-92d1 · keys in
 *   Android Keystore").
 */
data class IdentityCardState(val displayName: String, val avatarInitials: String, val metaLine: String)

/**
 * System instructions card slice.
 *
 * @property value Current textarea content.
 * @property placeholder Empty-state placeholder shown inside the textarea
 *   when [value] is blank.
 * @property variableChips Available `$VARIABLE` chip placeholders sorted
 *   alphabetically. Tapping a chip inserts the placeholder at the cursor.
 * @property characterCount Live character count for the counter footer.
 * @property characterLimit Maximum allowed characters (counter denominator).
 * @property approximateTokens Token-count estimate displayed alongside the
 *   character counter.
 * @property helperText Copy beneath the counter ("Prepended to every system
 *   prompt the agent sends.").
 * @property validationError Optional inline error rendered beneath the
 *   counter (e.g. "exceeds character limit").
 */
data class SystemInstructionsCardState(
    val value: String,
    val placeholder: String,
    val variableChips: List<String>,
    val characterCount: Int,
    val characterLimit: Int,
    val approximateTokens: Int,
    val helperText: String,
    val validationError: String?,
)

/**
 * Restrictions card slice.
 *
 * @property approveSelection Currently selected segmented option.
 * @property approveAllLabel / [approveSensitiveLabel] / [approveNeverLabel]
 *   Localized segmented control labels.
 * @property blockDestructive Whether the "Block destructive tools" toggle is on.
 * @property blockDestructiveSubtitle Localized helper subtitle.
 * @property blockNetwork Whether the "Block network from local model" toggle is on.
 * @property blockNetworkSubtitle Localized helper subtitle.
 * @property capSteps Trailing-number value for the "Cap autonomous steps" row.
 */
data class RestrictionsCardState(
    val approveSelection: ApproveToolCallsOption,
    val approveAllLabel: String,
    val approveSensitiveLabel: String,
    val approveNeverLabel: String,
    val blockDestructive: Boolean,
    val blockDestructiveSubtitle: String,
    val blockNetwork: Boolean,
    val blockNetworkSubtitle: String,
    val capSteps: Int,
    val capStepsSubtitle: String,
)

/**
 * Single sampling-parameter slider. Exposed as data so the catalog can
 * render the LLM parameters card without owning the labels.
 *
 * @property id Stable id for the row — used as the LazyColumn key and as
 *   the test tag.
 * @property title Row label.
 * @property valueLabel Localised display value (e.g. "0.90").
 * @property value Slider position (`[valueRange.start, valueRange.endInclusive]`).
 * @property valueRange Slider domain.
 * @property steps Discrete steps; `0` for continuous sliders.
 */
data class LlmParameterSlider(
    val id: String,
    val title: String,
    val valueLabel: String,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
)

data class LlmParametersCardState(val sliders: List<LlmParameterSlider>)

/**
 * Local-model card slice.
 *
 * @property modelName Active model name (null when no model is installed
 *   — the card then renders an empty state).
 * @property metaLine "1.4 GB · 2 048 ctx · Q4_K_M · downloaded 12 May".
 * @property backendLabel Localized inference-backend label, e.g. "NPU (QNN)
 *   · auto-fallback to GPU then CPU.".
 * @property backendOptions Available backend dropdown options.
 * @property selectedBackend Currently selected backend key.
 * @property testProbeText Subtitle for the Test backend row. The screen
 *   formats the [ai.agent.android.domain.models.TestProbeResult] before
 *   passing it down so the catalog stays format-agnostic.
 * @property testProbeIsError `true` when the last probe failed — drives
 *   the error tint on the subtitle.
 */
data class LocalModelCardState(
    val modelName: String?,
    val metaLine: String?,
    val backendLabel: String,
    val backendOptions: List<String>,
    val selectedBackend: String,
    val testProbeText: String,
    val testProbeIsError: Boolean,
)

/**
 * External-provider list row.
 *
 * @property id Stable id (e.g. "openai", "ollama") for keying & test tags.
 * @property title Display name (`"OpenAI"`).
 * @property fingerprint Masked API-key fingerprint or `null` for
 *   not-configured providers.
 * @property model Currently selected model name; `null` when unset.
 * @property endpointHint Optional secondary line (e.g. Ollama base URL).
 * @property isLan `true` for LAN-local providers — drives the LAN pill.
 */
data class ProviderRowState(
    val id: String,
    val title: String,
    val fingerprint: String?,
    val model: String?,
    val endpointHint: String?,
    val isLan: Boolean,
)

data class ExternalProvidersCardState(val rows: List<ProviderRowState>)

/**
 * Memory stats stat-cell payload.
 *
 * @property label Cell label ("Chunks", "Size", "Threads", "Avg score").
 * @property value Pre-formatted value ("1 248", "14.2 MB", "—", "0.74").
 */
data class MemoryStatCell(val label: String, val value: String)

/**
 * One selectable embedding provider in the Memory-section dropdown.
 *
 * @property id Stable provider id used as the selection key (e.g. `"use"`).
 * @property label Human-readable provider name rendered in the dropdown.
 */
data class EmbeddingOptionRow(val id: String, val label: String)

/**
 * One tunable numeric memory parameter rendered as a labelled slider row
 * (`ParamSliderRow`) inside the Memory card — the same density as the
 * LLM-parameters card.
 *
 * @property id Stable id for the row — used as the LazyColumn key, the test
 *   tag, and the discriminator passed back through
 *   [SettingsCallbacks.onMemoryParamChange].
 * @property title Row label.
 * @property valueLabel Localised display value (e.g. `"5"`, `"0.55"`, `"30 d"`).
 * @property value Slider position.
 * @property valueRange Slider domain.
 * @property steps Discrete steps; `0` for continuous sliders.
 */
data class MemoryParamSlider(
    val id: String,
    val title: String,
    val valueLabel: String,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
)

/**
 * Memory card slice.
 *
 * @property params Tunable numeric memory parameters (top-K, threshold,
 *   recency half-life, compaction age, max chunks) rendered as branded sliders.
 * @property compactionEnabled Whether the background compaction toggle is on.
 * @property compactionLabel / [compactionSubtitle] Localised toggle copy.
 * @property embeddingOptions Available embedding providers for the dropdown.
 * @property selectedEmbeddingId Wire id of the currently selected provider.
 * @property selectedEmbeddingLabel Display name of the selected provider.
 * @property validationError Optional inline error rendered beneath the
 *   sliders when the last tuning edit was rejected.
 */
data class MemoryCardState(
    val stats: List<MemoryStatCell>,
    val autoExtractEnabled: Boolean,
    val autoExtractLabel: String,
    val autoExtractSubtitle: String,
    val autoSummarizeThreshold: Int,
    val autoSummarizeLabel: String,
    val params: List<MemoryParamSlider>,
    val compactionEnabled: Boolean,
    val compactionLabel: String,
    val compactionSubtitle: String,
    val embeddingTitle: String,
    val embeddingOptions: List<EmbeddingOptionRow>,
    val selectedEmbeddingId: String,
    val selectedEmbeddingLabel: String,
    val exportLabel: String,
    val importLabel: String,
    val reembedLabel: String,
    val clearLabel: String,
    val validationError: String? = null,
    /** Re-embed progress in `0..100`, or `null` when no re-embed is in flight. */
    val reembedProgressPercent: Int?,
)

data class NotificationsCardState(val longRunningEnabled: Boolean)

/**
 * Privacy card slice. Carries the crash-reporting toggle and the verbose
 * memory-logging diagnostic toggle.
 *
 * @property crashReportingEnabled Whether anonymous crash reporting is on.
 * @property verboseMemoryLoggingEnabled Whether verbose memory diagnostics
 *   (per-hit console snippets + scores, compaction membership logs) are on.
 */
data class PrivacyCardState(val crashReportingEnabled: Boolean, val verboseMemoryLoggingEnabled: Boolean = false)

/**
 * Top-level input to `SettingsContent`. Carries every card slice plus the
 * surface-wide [visualState] flag.
 *
 * @property visualState Drives overlays (restart-required banner, typed-
 *   confirm dialog, loading skeleton).
 * @property subtitleVersion / [subtitleChannel] / [subtitleBuildDate] Top-
 *   app-bar subtitle pieces.
 * @property identity Identity card payload; `null` while loading.
 * @property systemInstructions System instructions payload.
 * @property restrictions Restrictions card payload.
 * @property llmParameters LLM parameters payload.
 * @property localModel Local-model card payload.
 * @property externalProviders External providers list payload.
 * @property memory Memory card payload.
 * @property notifications Notifications card payload.
 * @property privacy Privacy card payload (crash reporting toggle).
 * @property restartRequiredMessage Banner copy when visualState ==
 *   RestartRequired.
 * @property destructiveAction Typed-confirm payload when visualState ==
 *   DestructiveAction.
 */
data class SettingsViewState(
    val visualState: SettingsVisualState,
    val subtitleVersion: String,
    val subtitleChannel: String,
    val subtitleBuildDate: String,
    val identity: IdentityCardState?,
    val systemInstructions: SystemInstructionsCardState,
    val restrictions: RestrictionsCardState,
    val llmParameters: LlmParametersCardState,
    val localModel: LocalModelCardState,
    val externalProviders: ExternalProvidersCardState,
    val memory: MemoryCardState,
    val notifications: NotificationsCardState,
    val privacy: PrivacyCardState,
    val restartRequiredMessage: String? = null,
    val destructiveAction: DestructiveActionState? = null,
) {
    init {
        require(
            (visualState == SettingsVisualState.RestartRequired) == (restartRequiredMessage != null),
        ) {
            "restartRequiredMessage must be non-null iff visualState == RestartRequired"
        }
        require(
            (visualState == SettingsVisualState.DestructiveAction) == (destructiveAction != null),
        ) {
            "destructiveAction must be non-null iff visualState == DestructiveAction"
        }
    }
}

/**
 * Destructive typed-confirm dialog payload. The screen renders the
 * dialog through `SettingsContent`; the typed-confirm field is enabled
 * only when [pendingInput] (trim+lowercased) equals [keyword].
 *
 * @property title Localized dialog title.
 * @property body Localized dialog body explaining the scope of the action.
 * @property keyword Word the user must type to enable the confirm button
 *   (defaults to `"yes"`).
 * @property hint Placeholder shown in the typed-confirm field.
 * @property pendingInput Current value of the typed-confirm field.
 * @property kind Tag identifying which action the dialog is gating.
 */
data class DestructiveActionState(
    val title: String,
    val body: String,
    val keyword: String,
    val hint: String,
    val pendingInput: String,
    val kind: DestructiveActionKind,
)

enum class DestructiveActionKind {
    ClearMemory,
    ResetSettings,
}

/**
 * Bundle of typed callbacks invoked by `SettingsContent`. Kept as a single
 * data class so the call site stays compact even with the new
 * card-based surface.
 */
@Suppress("LongParameterList")
class SettingsCallbacks(
    val onBack: () -> Unit = {},

    // System instructions.
    val onSystemInstructionsChange: (String) -> Unit = {},
    val onInsertVariableClick: () -> Unit = {},
    val onChipInsert: (String) -> Unit = {},

    // Restrictions.
    val onApproveSelectionChange: (ApproveToolCallsOption) -> Unit = {},
    val onBlockDestructiveChange: (Boolean) -> Unit = {},
    val onBlockNetworkChange: (Boolean) -> Unit = {},
    val onCapStepsChange: (Int) -> Unit = {},

    // LLM parameters.
    val onSliderChange: (id: String, value: Float) -> Unit = { _, _ -> },
    val onResetLlmDefaults: () -> Unit = {},

    // Local model.
    val onManageModelsClick: () -> Unit = {},
    val onBackendSelected: (String) -> Unit = {},
    val onTestBackendClick: () -> Unit = {},

    // External providers.
    val onProviderRowClick: (String) -> Unit = {},
    val onAddProviderClick: () -> Unit = {},

    // Memory.
    val onAutoExtractToggle: (Boolean) -> Unit = {},
    val onAutoSummarizeChange: (Int) -> Unit = {},
    val onMemoryParamChange: (id: String, value: Float) -> Unit = { _, _ -> },
    val onMemoryCompactionToggle: (Boolean) -> Unit = {},
    val onEmbeddingProviderSelected: (String) -> Unit = {},
    val onExportMemoryClick: () -> Unit = {},
    val onImportMemoryClick: () -> Unit = {},
    val onReembedClick: () -> Unit = {},
    val onClearMemoryClick: () -> Unit = {},

    // Notifications.
    val onLongRunningToggle: (Boolean) -> Unit = {},

    // Privacy.
    val onCrashReportingToggle: (Boolean) -> Unit = {},
    val onVerboseMemoryLoggingToggle: (Boolean) -> Unit = {},
    val onResetSettingsClick: () -> Unit = {},

    // Surface-wide.
    val onRestartClick: () -> Unit = {},
    val onDestructiveTypedConfirmChange: (String) -> Unit = {},
    val onDestructiveConfirm: () -> Unit = {},
    val onDestructiveCancel: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopSettingsCallbacks(): SettingsCallbacks = SettingsCallbacks()
