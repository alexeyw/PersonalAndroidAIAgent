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

/**
 * ViewModel responsible for managing the Tools and Model Context Protocol (MCP) servers.
 * It handles the state for the Tools screen, including loading available local tools,
 * toggling their enabled/disabled state, and managing MCP server URLs.
 *
 * @property settingsRepository Repository to manage user settings such as disabled tools and MCP servers.
 * @property toolRepository Repository to fetch and manage the available local tools.
 */
@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val toolRepository: ToolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    
    /**
     * Exposes the current UI state of the Tools screen as a read-only StateFlow.
     */
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

    /**
     * Updates the text input field for a new MCP server URL.
     *
     * @param url The current string in the text input field.
     */
    fun onMcpUrlInputChanged(url: String) {
        _uiState.update { it.copy(newMcpUrlInput = url) }
    }

    /**
     * Adds the currently entered MCP server URL to the repository and clears the input field.
     * This allows the agent to connect to a new external MCP tool server.
     */
    fun addMcpServer() {
        val url = _uiState.value.newMcpUrlInput
        if (url.isNotBlank()) {
            viewModelScope.launch {
                settingsRepository.addMcpServerUrl(url)
                _uiState.update { it.copy(newMcpUrlInput = "") }
            }
        }
    }

    /**
     * Removes an existing MCP server URL from the repository, disconnecting the tools provided by it.
     *
     * @param url The exact URL of the MCP server to remove.
     */
    fun removeMcpServer(url: String) {
        viewModelScope.launch {
            settingsRepository.removeMcpServerUrl(url)
        }
    }

    /**
     * Toggles the enabled/disabled state of a local application function (tool).
     *
     * @param toolName The unique name of the local tool to toggle.
     * @param isEnabled True to enable the tool, false to disable it.
     */
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
