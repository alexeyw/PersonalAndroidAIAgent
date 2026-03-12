package ai.agent.android.presentation.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val toolRepository: ToolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tools = toolRepository.getAllLocalTools()
            _uiState.update { it.copy(localTools = tools) }
        }

        settingsRepository.mcpServerUrls
            .onEach { urls ->
                _uiState.update { it.copy(mcpServers = urls.toList()) }
            }
            .launchIn(viewModelScope)

        settingsRepository.disabledAppFunctions
            .onEach { disabled ->
                _uiState.update { it.copy(disabledAppFunctions = disabled) }
            }
            .launchIn(viewModelScope)
    }

    fun onMcpUrlInputChanged(url: String) {
        _uiState.update { it.copy(newMcpUrlInput = url) }
    }

    fun addMcpServer() {
        val url = _uiState.value.newMcpUrlInput
        if (url.isNotBlank()) {
            viewModelScope.launch {
                settingsRepository.addMcpServerUrl(url)
                _uiState.update { it.copy(newMcpUrlInput = "") }
            }
        }
    }

    fun removeMcpServer(url: String) {
        viewModelScope.launch {
            settingsRepository.removeMcpServerUrl(url)
        }
    }

    fun toggleLocalTool(toolName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val currentDisabled = _uiState.value.disabledAppFunctions.toMutableSet()
            if (isEnabled) {
                currentDisabled.remove(toolName)
            } else {
                currentDisabled.add(toolName)
            }
            settingsRepository.setDisabledAppFunctions(currentDisabled)
        }
    }
}
