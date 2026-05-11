package ai.agent.android.presentation.ui.settings

import ai.agent.android.R
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ApiKeyRepository
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
            settingsRepository.setPipelineMaxSteps(steps.coerceIn(5, 100))
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
                delay(500)
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
     * Updates the OpenAI model name.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateOpenAiModel(model: String) {
        viewModelScope.launch {
            apiKeyRepository.setOpenAIModel(model.takeIf { it.isNotBlank() })
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
     * Updates the Anthropic model name.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateAnthropicModel(model: String) {
        viewModelScope.launch {
            apiKeyRepository.setAnthropicModel(model.takeIf { it.isNotBlank() })
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
     * Updates the Google model name.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateGoogleModel(model: String) {
        viewModelScope.launch {
            apiKeyRepository.setGoogleModel(model.takeIf { it.isNotBlank() })
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
     * Updates the DeepSeek model name.
     *
     * @param model The new model or empty string to clear.
     */
    fun updateDeepSeekModel(model: String) {
        viewModelScope.launch {
            apiKeyRepository.setDeepSeekModel(model.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Updates the Ollama local base URL.
     *
     * @param url The new URL or empty string to clear.
     */
    fun updateOllamaBaseUrl(url: String) {
        viewModelScope.launch {
            apiKeyRepository.setOllamaBaseUrl(url.takeIf { it.isNotBlank() })
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
            val size = window.toIntOrNull() ?: 4096
            apiKeyRepository.setOllamaContextWindowSize(size)
        }
    }
}
