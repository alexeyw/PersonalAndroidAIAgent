package ai.agent.android.presentation.ui.settings

import ai.agent.android.R
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.presentation.ui.common.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for managing the state and business logic of the Settings UI.
 *
 * @property settingsRepository The repository for managing application settings.
 * @property apiKeyRepository The repository for managing API keys.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val localModelRepository: LocalModelRepository,
    private val crashReportingRepository: CrashReportingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository.temperature.onEach { value ->
            _uiState.update { it.copy(temperature = value) }
        }.launchIn(viewModelScope)

        settingsRepository.topK.onEach { value ->
            _uiState.update { it.copy(topK = value) }
        }.launchIn(viewModelScope)

        settingsRepository.topP.onEach { value ->
            _uiState.update { it.copy(topP = value) }
        }.launchIn(viewModelScope)

        settingsRepository.maxContextLength.onEach { value ->
            _uiState.update { it.copy(maxContextLength = value) }
        }.launchIn(viewModelScope)

        settingsRepository.systemPromptPrefix.onEach { value ->
            _uiState.update { it.copy(systemPromptPrefix = value) }
        }.launchIn(viewModelScope)

        settingsRepository.requiresUserConfirmation.onEach { value ->
            _uiState.update { it.copy(requiresUserConfirmation = value) }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOpenAIKey().onEach { value ->
            _uiState.update { it.copy(openAiKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOpenAIModel().onEach { value ->
            _uiState.update { it.copy(openAiModel = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getAnthropicKey().onEach { value ->
            _uiState.update { it.copy(anthropicKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getAnthropicModel().onEach { value ->
            _uiState.update { it.copy(anthropicModel = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getGoogleKey().onEach { value ->
            _uiState.update { it.copy(googleKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getGoogleModel().onEach { value ->
            _uiState.update { it.copy(googleModel = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getDeepSeekKey().onEach { value ->
            _uiState.update { it.copy(deepSeekKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getDeepSeekModel().onEach { value ->
            _uiState.update { it.copy(deepSeekModel = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOllamaBaseUrl().onEach { value ->
            _uiState.update { it.copy(ollamaBaseUrl = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOllamaModelName().onEach { value ->
            _uiState.update { it.copy(ollamaModel = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOllamaContextWindowSize().onEach { value ->
            _uiState.update { it.copy(ollamaContextWindow = value.toString()) }
        }.launchIn(viewModelScope)
        settingsRepository.localModelBackend.onEach { value ->
            _uiState.update { it.copy(localModelBackend = value) }
        }.launchIn(viewModelScope)

        settingsRepository.pipelineMaxSteps.onEach { value ->
            _uiState.update { it.copy(pipelineMaxSteps = value) }
        }.launchIn(viewModelScope)

        settingsRepository.crashReportingEnabled.onEach { value ->
            _uiState.update { it.copy(crashReportingEnabled = value) }
        }.launchIn(viewModelScope)

        settingsRepository.memorySummaryDefaultLimit.onEach { value ->
            _uiState.update { it.copy(memorySummaryDefaultLimit = value) }
        }.launchIn(viewModelScope)
    }

    /**
     * Updates the default limit applied to the `$MEMORY_SUMMARY` prompt
     * variable. The repository persists the raw value; this VM coerces the
     * input to the documented `1..50` bracket so a misbehaving slider can
     * never write an out-of-range preference.
     *
     * @param limit Desired upper bound on the number of memory chunks
     *   inlined into the prompt summary.
     */
    fun updateMemorySummaryDefaultLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setMemorySummaryDefaultLimit(
                limit.coerceIn(
                    SettingsDefaults.MEMORY_SUMMARY_LIMIT_MIN,
                    SettingsDefaults.MEMORY_SUMMARY_LIMIT_MAX,
                ),
            )
        }
    }

    /**
     * Updates the crash-reporting opt-in flag. Also pushes the new value to
     * [CrashReportingRepository] so Firebase collection toggles immediately
     * rather than waiting for the next process restart.
     *
     * @param enabled `true` after the user accepted the consent dialog,
     *                `false` to disable reporting and revert to no-op mode.
     */
    fun updateCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReportingEnabled(enabled)
            crashReportingRepository.setEnabled(enabled)
        }
    }

    fun updateLocalModelBackend(backend: String) {
        viewModelScope.launch {
            settingsRepository.setLocalModelBackend(backend)
        }
    }

    /**
     * Updates the maximum number of pipeline execution steps.
     * The value is coerced to the valid range of 5–100.
     *
     * @param steps The desired maximum steps value.
     */
    fun updatePipelineMaxSteps(steps: Int) {
        viewModelScope.launch {
            settingsRepository.setPipelineMaxSteps(
                steps.coerceIn(
                    SettingsDefaults.PIPELINE_MAX_STEPS_MIN,
                    SettingsDefaults.PIPELINE_MAX_STEPS_MAX,
                ),
            )
        }
    }

    fun testBackend(onResult: (UiText) -> Unit) {
        viewModelScope.launch {
            val activeModel = localModelRepository.getActiveModel()
            if (activeModel == null) {
                onResult(UiText(R.string.errors_settings_no_active_model))
                return@launch
            }

            try {
                // Ensure settings are flushed
                delay(BACKEND_TEST_FLUSH_DELAY_MS)
                val result = loadModelUseCase(activeModel.path)
                if (result is Result.Success) {
                    onResult(UiText(R.string.settings_backend_test_success))
                } else if (result is Result.Error) {
                    onResult(UiText.of(R.string.errors_settings_test_backend_failed, result.message ?: ""))
                }
            } catch (e: Exception) {
                onResult(UiText.of(R.string.errors_settings_test_backend_exception, e.message ?: ""))
            }
        }
    }

    /**
     * Updates the generation temperature setting.
     *
     * @param temperature The new temperature value.
     */
    fun updateTemperature(temperature: Float) {
        viewModelScope.launch {
            settingsRepository.setTemperature(temperature)
        }
    }

    /**
     * Updates the generation top-k setting.
     *
     * @param topK The new top-k value.
     */
    fun updateTopK(topK: Int) {
        viewModelScope.launch {
            settingsRepository.setTopK(topK)
        }
    }

    /**
     * Updates the generation top-p setting.
     *
     * @param topP The new top-p value.
     */
    fun updateTopP(topP: Float) {
        viewModelScope.launch {
            settingsRepository.setTopP(topP)
        }
    }

    /**
     * Updates the maximum context length setting.
     *
     * @param length The new context length value.
     */
    fun updateMaxContextLength(length: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxContextLength(length)
        }
    }

    /**
     * Updates the system prompt prefix.
     *
     * @param prompt The new system prompt.
     */
    fun updateSystemPromptPrefix(prompt: String) {
        viewModelScope.launch {
            settingsRepository.setSystemPromptPrefix(prompt)
        }
    }

    /**
     * Updates the human-in-the-loop requirement.
     *
     * @param required Whether user confirmation is required.
     */
    fun updateRequiresUserConfirmation(required: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRequiresUserConfirmation(required)
        }
    }

    /**
     * Updates the OpenAI API key.
     *
     * @param key The new key or empty string to clear.
     */
    fun updateOpenAiKey(key: String) {
        viewModelScope.launch {
            apiKeyRepository.setOpenAIKey(key.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the OpenAI model name. Tracks the row as `PendingChange`
     * until the repository flush completes so the per-row `KnotworkLoader`
     * spins for the duration of the async write.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateOpenAiModel(model: String) {
        markPending(ROW_ID_OPENAI)
        viewModelScope.launch {
            apiKeyRepository.setOpenAIModel(model.takeIf { it.isNotBlank() })
            clearPending(ROW_ID_OPENAI)
        }
    }

    /**
     * Updates the Anthropic API key.
     *
     * @param key The new key or empty string to clear.
     */
    fun updateAnthropicKey(key: String) {
        viewModelScope.launch {
            apiKeyRepository.setAnthropicKey(key.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the Anthropic model name. Tracks the row as `PendingChange`
     * until the repository flush completes.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateAnthropicModel(model: String) {
        markPending(ROW_ID_ANTHROPIC)
        viewModelScope.launch {
            apiKeyRepository.setAnthropicModel(model.takeIf { it.isNotBlank() })
            clearPending(ROW_ID_ANTHROPIC)
        }
    }

    /**
     * Updates the Google API key.
     *
     * @param key The new key or empty string to clear.
     */
    fun updateGoogleKey(key: String) {
        viewModelScope.launch {
            apiKeyRepository.setGoogleKey(key.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the Google model name. Tracks the row as `PendingChange`
     * until the repository flush completes.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateGoogleModel(model: String) {
        markPending(ROW_ID_GOOGLE)
        viewModelScope.launch {
            apiKeyRepository.setGoogleModel(model.takeIf { it.isNotBlank() })
            clearPending(ROW_ID_GOOGLE)
        }
    }

    /**
     * Updates the DeepSeek API key.
     *
     * @param key The new key or empty string to clear.
     */
    fun updateDeepSeekKey(key: String) {
        viewModelScope.launch {
            apiKeyRepository.setDeepSeekKey(key.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the DeepSeek model name. Tracks the row as `PendingChange`
     * until the repository flush completes.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateDeepSeekModel(model: String) {
        markPending(ROW_ID_DEEPSEEK)
        viewModelScope.launch {
            apiKeyRepository.setDeepSeekModel(model.takeIf { it.isNotBlank() })
            clearPending(ROW_ID_DEEPSEEK)
        }
    }

    /**
     * Updates the Ollama local base URL. A blank value surfaces an inline
     * validation error on the row; a non-blank value clears it.
     *
     * @param url The new URL or empty string to clear.
     */
    fun updateOllamaBaseUrl(url: String) {
        _uiState.update {
            it.copy(
                ollamaBaseUrl = url,
                ollamaBaseUrlInvalid = url.isBlank(),
                pendingRowIds = it.pendingRowIds + ROW_ID_OLLAMA,
            )
        }
        viewModelScope.launch {
            apiKeyRepository.setOllamaBaseUrl(url.takeIf { it.isNotBlank() })
            _uiState.update { it.copy(pendingRowIds = it.pendingRowIds - ROW_ID_OLLAMA) }
        }
    }

    /**
     * Updates the Ollama model name.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateOllamaModel(model: String) {
        viewModelScope.launch {
            apiKeyRepository.setOllamaModelName(model.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the Ollama context window size.
     *
     * @param window The context window size string.
     */
    fun updateOllamaContextWindow(window: String) {
        viewModelScope.launch {
            val size = window.toIntOrNull() ?: SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT
            apiKeyRepository.setOllamaContextWindowSize(size)
        }
    }

    private fun markPending(rowId: String) {
        _uiState.update { it.copy(pendingRowIds = it.pendingRowIds + rowId) }
    }

    private fun clearPending(rowId: String) {
        _uiState.update { it.copy(pendingRowIds = it.pendingRowIds - rowId) }
    }

    private companion object {
        /**
         * Short delay between writing the backend setting and triggering the test
         * load, giving DataStore time to flush so the loader observes the new value.
         */
        const val BACKEND_TEST_FLUSH_DELAY_MS: Long = 500L

        /**
         * Stable row identifiers used by the Knotwork settings surface. Kept
         * in lock-step with `SettingsRowIds` consumed from the UI layer.
         */
        const val ROW_ID_OPENAI = "openai"
        const val ROW_ID_ANTHROPIC = "anthropic"
        const val ROW_ID_GOOGLE = "google"
        const val ROW_ID_DEEPSEEK = "deepseek"
        const val ROW_ID_OLLAMA = "ollama"
    }
}
