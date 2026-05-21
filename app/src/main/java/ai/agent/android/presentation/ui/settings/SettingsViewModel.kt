package ai.agent.android.presentation.ui.settings

import ai.agent.android.R
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.ProviderId
import ai.agent.android.domain.models.ProviderSummary
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.IdentityRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.ClearAllMemoryUseCase
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import ai.agent.android.domain.usecases.GetSystemPromptVariableCatalogUseCase
import ai.agent.android.domain.usecases.ReembedAllMemoriesUseCase
import ai.agent.android.domain.usecases.ResetSamplingDefaultsUseCase
import ai.agent.android.domain.usecases.TestBackendUseCase
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStream
import javax.inject.Inject

/**
 * ViewModel backing the Phase 22 / Task 9 redesigned Settings screen.
 *
 * Aggregates every persisted preference + repository projection into a
 * single [SettingsUiState] and exposes typed mutator methods grouped by
 * card (System instructions / Restrictions / LLM params / Local model /
 * External providers / Memory / Notifications / Privacy / About).
 *
 * Restart-required logic — the VM snapshots the initial values of
 * `localModelBackend` and `ollamaBaseUrl` (the two preferences known to
 * require a process restart) on first load, then flips
 * [SettingsUiState.restartRequired] when the live values diverge.
 *
 * Destructive actions stage a [PendingDestructiveAction] which the
 * screen surfaces as a typed-confirm dialog before calling the
 * corresponding `confirm…` method.
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val localModelRepository: LocalModelRepository,
    private val memoryRepository: MemoryRepository,
    private val identityRepository: IdentityRepository,
    private val crashReportingRepository: CrashReportingRepository,
    private val testBackendUseCase: TestBackendUseCase,
    private val resetSamplingDefaultsUseCase: ResetSamplingDefaultsUseCase,
    private val clearAllMemoryUseCase: ClearAllMemoryUseCase,
    private val exportMemoryBaseUseCase: ExportMemoryBaseUseCase,
    private val reembedAllMemoriesUseCase: ReembedAllMemoriesUseCase,
    private val getSystemPromptVariableCatalogUseCase: GetSystemPromptVariableCatalogUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Initial `localModelBackend` value; baseline for restart-required detection.
     * `null` is a legitimate baseline value (when no model has been picked yet),
     * so the dedicated [hasCapturedBackendBaseline] flag — not the field's
     * nullability — decides whether the baseline has been seen.
     */
    private var appliedBackend: String? = null
    private var hasCapturedBackendBaseline: Boolean = false

    /**
     * Initial `ollamaBaseUrl` value; baseline for restart-required detection.
     * Captured on the very first emission of the providers flow (even when the
     * URL is `null`) so the `null → some URL` and `some URL → null` transitions
     * are both detected. Until the providers flow has emitted at least once we
     * cannot derive Ollama-side restart-required, hence the explicit flag.
     */
    private var appliedOllamaBaseUrl: String? = null
    private var hasCapturedOllamaBaseline: Boolean = false

    init {
        loadIdentity()
        loadVariableCatalog()
        // Restart-required baselines MUST be locked in before live observers
        // start emitting. Reactive DataStore / EncryptedPrefs flows can deliver
        // a transient seed value (null / default) before the persisted one
        // settles; capturing the baseline from the first such emission would
        // race the steady-state value and mis-flag the very next emission as
        // a "change", surfacing a spurious restart banner on every open. By
        // gating `observePreferences()` on the completion of the baseline
        // `.first()` reads we guarantee that — on a fresh VM — baseline ==
        // persisted == first observed value, so restart-required stays false
        // until the user actually changes something.
        viewModelScope.launch {
            appliedBackend = settingsRepository.localModelBackend.first()
            hasCapturedBackendBaseline = true
            appliedOllamaBaseUrl = apiKeyRepository.getOllamaBaseUrl().firstOrNull()
            hasCapturedOllamaBaseline = true
            observePreferences()
        }
    }

    // ─── Initial loads ──────────────────────────────────────────────────────

    private fun loadIdentity() {
        viewModelScope.launch {
            val anonymous = appContext.getString(R.string.settings_identity_display_name)
            val identity = identityRepository.getIdentity(anonymous)
            _uiState.update { it.copy(identity = identity) }
        }
    }

    private fun loadVariableCatalog() {
        viewModelScope.launch {
            val entries = getSystemPromptVariableCatalogUseCase()
                .map { VariableCatalogChip(it.placeholder, it.sample) }
            _uiState.update { it.copy(variableCatalog = entries) }
        }
    }

    @Suppress("LongMethod")
    private fun observePreferences() {
        settingsRepository.systemPromptPrefix.onEach { value ->
            _uiState.update { it.copy(systemInstructions = value) }
        }.launchIn(viewModelScope)

        settingsRepository.toolApprovalPolicy.onEach { value ->
            _uiState.update { it.copy(toolApprovalPolicy = value) }
        }.launchIn(viewModelScope)

        settingsRepository.blockDestructiveTools.onEach { value ->
            _uiState.update { it.copy(blockDestructiveTools = value) }
        }.launchIn(viewModelScope)

        settingsRepository.blockNetworkFromLocalModel.onEach { value ->
            _uiState.update { it.copy(blockNetworkFromLocalModel = value) }
        }.launchIn(viewModelScope)

        settingsRepository.pipelineMaxSteps.onEach { value ->
            _uiState.update { it.copy(capAutonomousSteps = value) }
        }.launchIn(viewModelScope)

        settingsRepository.temperature.onEach { value ->
            _uiState.update { it.copy(temperature = value) }
        }.launchIn(viewModelScope)
        settingsRepository.topK.onEach { value ->
            _uiState.update { it.copy(topK = value) }
        }.launchIn(viewModelScope)
        settingsRepository.topP.onEach { value ->
            _uiState.update { it.copy(topP = value) }
        }.launchIn(viewModelScope)
        settingsRepository.repetitionPenalty.onEach { value ->
            _uiState.update { it.copy(repetitionPenalty = value) }
        }.launchIn(viewModelScope)
        settingsRepository.maxContextLength.onEach { value ->
            _uiState.update { it.copy(maxContextLength = value) }
        }.launchIn(viewModelScope)

        localModelRepository.observeActiveModelMeta().onEach { meta ->
            _uiState.update { it.copy(activeModelMeta = meta) }
        }.launchIn(viewModelScope)

        settingsRepository.localModelBackend.onEach { value ->
            _uiState.update {
                it.copy(
                    localModelBackend = value,
                    restartRequired = restartRequired(backend = value, ollamaUrl = it.providers.ollamaBaseUrl()),
                )
            }
        }.launchIn(viewModelScope)

        settingsRepository.lastTestProbeResult.onEach { value ->
            _uiState.update { it.copy(lastTestProbeResult = value) }
        }.launchIn(viewModelScope)

        observeProviders()

        memoryRepository.observeStats().onEach { stats ->
            _uiState.update { it.copy(memoryStats = stats) }
        }.launchIn(viewModelScope)

        settingsRepository.autoSummarizeThreshold.onEach { value ->
            _uiState.update { it.copy(autoSummarizeThreshold = value) }
        }.launchIn(viewModelScope)

        settingsRepository.longRunningTaskNotificationsEnabled.onEach { value ->
            _uiState.update { it.copy(longRunningTaskNotificationsEnabled = value) }
        }.launchIn(viewModelScope)

        settingsRepository.crashReportingEnabled.onEach { value ->
            _uiState.update { it.copy(crashReportingEnabled = value) }
        }.launchIn(viewModelScope)
    }

    private fun observeProviders() {
        // Provider summaries are derived from ApiKeyRepository flows; we
        // collapse the 11 individual key/model flows into a single
        // `providers` list with masked fingerprints so the UI receives a
        // single payload it can map straight to ProviderRowState.
        viewModelScope.launch {
            combineProviderFlows().collect { summaries ->
                val ollamaUrl = summaries.ollamaBaseUrl()
                _uiState.update {
                    it.copy(
                        providers = summaries,
                        restartRequired = restartRequired(
                            backend = it.localModelBackend,
                            ollamaUrl = ollamaUrl,
                        ),
                    )
                }
            }
        }
    }

    private fun combineProviderFlows() = kotlinx.coroutines.flow.combine(
        kotlinx.coroutines.flow.combine(
            apiKeyRepository.getOpenAIKey(),
            apiKeyRepository.getOpenAIModel(),
            ::Pair,
        ),
        kotlinx.coroutines.flow.combine(
            apiKeyRepository.getAnthropicKey(),
            apiKeyRepository.getAnthropicModel(),
            ::Pair,
        ),
        kotlinx.coroutines.flow.combine(
            apiKeyRepository.getGoogleKey(),
            apiKeyRepository.getGoogleModel(),
            ::Pair,
        ),
        kotlinx.coroutines.flow.combine(
            apiKeyRepository.getDeepSeekKey(),
            apiKeyRepository.getDeepSeekModel(),
            ::Pair,
        ),
        kotlinx.coroutines.flow.combine(
            apiKeyRepository.getOllamaBaseUrl(),
            apiKeyRepository.getOllamaModelName(),
            ::Pair,
        ),
    ) { openAi, anthropic, google, deepSeek, ollama ->
        listOf(
            providerSummary(ProviderId.OpenAi, "OpenAI", openAi.first, openAi.second),
            providerSummary(ProviderId.Anthropic, "Anthropic", anthropic.first, anthropic.second),
            providerSummary(ProviderId.Google, "Google", google.first, google.second),
            providerSummary(ProviderId.DeepSeek, "DeepSeek", deepSeek.first, deepSeek.second),
            providerSummary(
                id = ProviderId.Ollama,
                displayName = "Ollama",
                key = ollama.first?.takeIf { it.isNotBlank() },
                model = ollama.second,
                endpointHint = ollama.first?.takeIf { it.isNotBlank() },
                isLan = true,
            ),
        )
    }

    private fun providerSummary(
        id: ProviderId,
        displayName: String,
        key: String?,
        model: String?,
        endpointHint: String? = null,
        isLan: Boolean = false,
    ): ProviderSummary = ProviderSummary(
        id = id,
        displayName = displayName,
        keyFingerprint = key?.takeIf { it.isNotBlank() }?.let { maskKey(it) },
        model = model?.takeIf { it.isNotBlank() },
        isLanLocal = isLan,
        endpointHint = endpointHint,
    )

    private fun maskKey(key: String): String {
        val tail = key.takeLast(MASK_TAIL_LENGTH)
        val prefix = key.takeWhile { it != '-' || key.indexOf('-') == 0 }
            .takeIf { it.isNotBlank() && key.length > MASK_PREFIX_THRESHOLD }
            ?.take(MASK_PREFIX_LENGTH)
            .orEmpty()
        return if (prefix.isBlank()) "…$tail" else "$prefix-…$tail"
    }

    /**
     * Restart-required is derived state — comparison-only. The baselines
     * are captured by the two source flows the first time they emit (so
     * `null → some URL` and `some URL → null` transitions for Ollama are
     * both detected). This function never mutates the baseline so the
     * call ordering between the backend and providers flows cannot race
     * the comparison.
     */
    private fun restartRequired(backend: String, ollamaUrl: String?): Boolean {
        val backendChanged = hasCapturedBackendBaseline && appliedBackend != backend
        val ollamaChanged = hasCapturedOllamaBaseline && appliedOllamaBaseUrl != ollamaUrl
        return backendChanged || ollamaChanged
    }

    // ─── System instructions ───────────────────────────────────────────────

    fun updateSystemInstructions(value: String) {
        viewModelScope.launch {
            settingsRepository.setSystemPromptPrefix(value)
        }
    }

    fun insertVariable(placeholder: String) {
        val current = _uiState.value.systemInstructions
        val updated = if (current.endsWith(' ') || current.isEmpty()) {
            "$current$placeholder"
        } else {
            "$current $placeholder"
        }
        updateSystemInstructions(updated)
    }

    // ─── Restrictions ──────────────────────────────────────────────────────

    fun setToolApprovalPolicy(policy: ToolApprovalPolicy) {
        viewModelScope.launch { settingsRepository.setToolApprovalPolicy(policy) }
    }

    fun setBlockDestructiveTools(blocked: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockDestructiveTools(blocked) }
    }

    fun setBlockNetworkFromLocalModel(blocked: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockNetworkFromLocalModel(blocked) }
    }

    fun setCapAutonomousSteps(steps: Int) {
        viewModelScope.launch { settingsRepository.setPipelineMaxSteps(steps) }
    }

    // ─── LLM parameters ────────────────────────────────────────────────────

    fun setTemperature(value: Float) {
        viewModelScope.launch { settingsRepository.setTemperature(value) }
    }

    fun setTopK(value: Int) {
        viewModelScope.launch { settingsRepository.setTopK(value) }
    }

    fun setTopP(value: Float) {
        viewModelScope.launch { settingsRepository.setTopP(value) }
    }

    fun setRepetitionPenalty(value: Float) {
        viewModelScope.launch { settingsRepository.setRepetitionPenalty(value) }
    }

    fun setMaxContextLength(value: Int) {
        viewModelScope.launch { settingsRepository.setMaxContextLength(value) }
    }

    fun resetSamplingDefaults() {
        viewModelScope.launch {
            resetSamplingDefaultsUseCase()
            emitSnackbar(appContext.getString(R.string.settings_llm_reset_defaults))
        }
    }

    // ─── Local model ──────────────────────────────────────────────────────

    fun setLocalModelBackend(backend: String) {
        viewModelScope.launch { settingsRepository.setLocalModelBackend(backend) }
    }

    fun runBackendProbe() {
        viewModelScope.launch {
            _uiState.update { it.copy(testProbeInFlight = true) }
            val result = testBackendUseCase()
            _uiState.update { it.copy(testProbeInFlight = false) }
            val message = if (result.success) {
                appContext.getString(
                    R.string.settings_row_test_backend_success,
                    result.tokensGenerated,
                    String.format(java.util.Locale.getDefault(), "%.2fs", result.durationMs / MS_PER_SECOND_F),
                    String.format(java.util.Locale.getDefault(), "%.1f", result.tokensPerSecond),
                )
            } else {
                appContext.getString(R.string.settings_row_test_backend_failed, result.errorMessage.orEmpty())
            }
            emitSnackbar(message)
        }
    }

    // ─── External providers ───────────────────────────────────────────────

    fun providerForId(id: String): ProviderId? = ProviderId.entries.firstOrNull { it.cloudProvider.id == id }

    // ─── Memory ───────────────────────────────────────────────────────────

    fun setAutoSummarizeThreshold(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setAutoSummarizeThreshold(percent.coerceIn(0, MAX_PERCENT).toFloat() / MAX_PERCENT)
        }
    }

    /**
     * Exports the entire memory base into the supplied [OutputStream].
     * Called by the screen after the SAF launcher returns a writable URI.
     */
    fun exportMemoryBase(target: OutputStream) {
        viewModelScope.launch {
            runCatching {
                exportMemoryBaseUseCase(target)
            }.onSuccess { count ->
                emitSnackbar(appContext.getString(R.string.settings_memory_export_success, count))
            }.onFailure { error ->
                Timber.w(error, "Memory export failed")
                emitSnackbar(appContext.getString(R.string.settings_memory_export_failed, error.message.orEmpty()))
            }
        }
    }

    fun runReembed() {
        viewModelScope.launch {
            reembedAllMemoriesUseCase().collect { progress ->
                _uiState.update { it.copy(reembedProgress = progress.takeIf { fraction -> fraction < 1f }) }
            }
            emitSnackbar(appContext.getString(R.string.settings_memory_reembed_done))
        }
    }

    fun stageClearMemory() {
        _uiState.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction.ClearMemory,
                destructiveTypedInput = "",
            )
        }
    }

    fun stageResetSettings() {
        _uiState.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction.ResetSettings,
                destructiveTypedInput = "",
            )
        }
    }

    fun updateDestructiveTypedInput(value: String) {
        _uiState.update { it.copy(destructiveTypedInput = value) }
    }

    fun cancelDestructive() {
        _uiState.update { it.copy(pendingDestructive = null, destructiveTypedInput = "") }
    }

    fun confirmDestructive() {
        val pending = _uiState.value.pendingDestructive ?: return
        val keyword = appContext.getString(R.string.settings_destructive_typed_keyword)
        if (!_uiState.value.destructiveTypedInput.trim().equals(keyword, ignoreCase = true)) return
        when (pending) {
            PendingDestructiveAction.ClearMemory -> performClearMemory()
            PendingDestructiveAction.ResetSettings -> performResetSettings()
        }
        _uiState.update { it.copy(pendingDestructive = null, destructiveTypedInput = "") }
    }

    private fun performClearMemory() {
        viewModelScope.launch {
            clearAllMemoryUseCase()
            emitSnackbar(appContext.getString(R.string.settings_memory_clear))
        }
    }

    private fun performResetSettings() {
        viewModelScope.launch {
            resetSamplingDefaultsUseCase()
            settingsRepository.setToolApprovalPolicy(ToolApprovalPolicy.DEFAULT)
            settingsRepository.setBlockDestructiveTools(false)
            settingsRepository.setBlockNetworkFromLocalModel(false)
            settingsRepository.setAutoSummarizeThreshold(SettingsDefaults.AUTO_SUMMARIZE_THRESHOLD_DEFAULT)
            settingsRepository.setLongRunningTaskNotificationsEnabled(true)
            settingsRepository.setSystemPromptPrefix("")
            emitSnackbar(appContext.getString(R.string.settings_reset_button))
        }
    }

    // ─── Notifications + privacy ─────────────────────────────────────────

    fun setLongRunningTaskNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLongRunningTaskNotificationsEnabled(enabled) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReportingEnabled(enabled)
            crashReportingRepository.setEnabled(enabled)
        }
    }

    // ─── Surface ───────────────────────────────────────────────────────────

    /** Called by the screen after a restart action; resets the baseline so the banner stays gone. */
    fun acknowledgeRestart() {
        val current = _uiState.value
        appliedBackend = current.localModelBackend
        appliedOllamaBaseUrl = current.providers.ollamaBaseUrl()
        hasCapturedBackendBaseline = true
        hasCapturedOllamaBaseline = true
        _uiState.update { it.copy(restartRequired = false) }
    }

    fun snackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun emitSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    companion object {
        private const val MASK_TAIL_LENGTH = 4
        private const val MASK_PREFIX_LENGTH = 2
        private const val MASK_PREFIX_THRESHOLD = 6
        private const val MAX_PERCENT = 100
        private const val MS_PER_SECOND_F = 1_000f

        /**
         * Re-exposed for tests: maps a [CloudProvider] back to its
         * [ProviderId].
         */
        fun providerIdOf(provider: CloudProvider): ProviderId =
            ProviderId.entries.first { it.cloudProvider == provider }
    }
}

private fun List<ProviderSummary>.ollamaBaseUrl(): String? = firstOrNull { it.id == ProviderId.Ollama }?.endpointHint
