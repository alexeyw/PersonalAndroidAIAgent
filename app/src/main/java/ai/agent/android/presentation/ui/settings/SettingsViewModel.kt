package ai.agent.android.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val apiKeyRepository: ApiKeyRepository
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

        apiKeyRepository.getAnthropicKey().onEach { value ->
            _uiState.update { it.copy(anthropicKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getGoogleKey().onEach { value ->
            _uiState.update { it.copy(googleKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getDeepSeekKey().onEach { value ->
            _uiState.update { it.copy(deepSeekKey = value ?: "") }
        }.launchIn(viewModelScope)

        apiKeyRepository.getOllamaBaseUrl().onEach { value ->
            _uiState.update { it.copy(ollamaBaseUrl = value ?: "") }
        }.launchIn(viewModelScope)
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
     * Updates the Ollama local base URL.
     *
     * @param url The new URL or empty string to clear.
     */
    fun updateOllamaBaseUrl(url: String) {
        viewModelScope.launch {
            apiKeyRepository.setOllamaBaseUrl(url.takeIf { it.isNotBlank() })
        }
    }
}
