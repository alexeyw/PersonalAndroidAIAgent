package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool
import ai.agent.android.domain.repositories.McpServerRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * ViewModel for the Tools screen.
 *
 * Surfaces three streams:
 *
 *  - **Local tools** — loaded once from `ToolRepository.getAllLocalTools`.
 *    The user toggles them via [toggleLocalTool], which writes to
 *    `SettingsRepository.disabledAppFunctions`.
 *  - **MCP servers** — combined from `SettingsRepository.mcpServerUrls`
 *    and the per-URL streams of `McpServerRepository`. Adding a server
 *    immediately triggers a `tools/list` fetch; removing one disconnects
 *    the underlying client.
 *  - **Disabled MCP tool ids** — separate set in
 *    `SettingsRepository.disabledMcpTools` (kept distinct from the
 *    AppFunction set to avoid namespace collisions).
 */
@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val toolRepository: ToolRepository,
    private val mcpServerRepository: McpServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    /**
     * Per-URL guards for the lifecycle of the per-server status / fetch
     * jobs. When a URL is removed from Settings we cancel its observer
     * and disconnect the repository entry; when a new URL appears we
     * spin a fresh observer and kick off `fetchToolList`.
     */
    private val serverObservers = ConcurrentHashMap<String, Job>()

    init {
        viewModelScope.launch {
            val tools = toolRepository.getAllLocalTools()
            _uiState.update { it.copy(localTools = tools) }
        }

        settingsRepository.mcpServerUrls
            .onEach { urls -> reconcileServerSet(urls) }
            .launchIn(viewModelScope)

        settingsRepository.disabledAppFunctions
            .onEach { disabled ->
                _uiState.update { it.copy(disabledAppFunctions = disabled) }
            }
            .launchIn(viewModelScope)

        settingsRepository.disabledMcpTools
            .onEach { disabled ->
                _uiState.update { it.copy(disabledMcpTools = disabled) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Reconciles [urls] against the in-memory snapshot map: spins up a
     * status observer for every newly-added URL, cancels the observer and
     * disconnects the underlying client for every removed URL, and rebuilds
     * the snapshot list so the UI sees the new order.
     */
    private fun reconcileServerSet(urls: Set<String>) {
        val existing = serverObservers.keys.toSet()
        val toRemove = existing - urls
        val toAdd = urls - existing

        toRemove.forEach { url ->
            serverObservers.remove(url)?.cancel()
            viewModelScope.launch { mcpServerRepository.disconnect(serverUrl = url) }
        }
        toAdd.forEach { url ->
            serverObservers[url] = observeServer(url)
            fetchToolsAndUpdate(serverUrl = url, forceRefresh = false)
        }

        _uiState.update { state ->
            val existingByUrl = state.mcpServers.associateBy { it.url }
            val snapshots = urls.map { url ->
                existingByUrl[url] ?: McpServerSnapshot(
                    url = url,
                    status = McpConnectionStatus.Connecting,
                    tools = emptyList(),
                )
            }
            state.copy(
                mcpServers = snapshots,
                expandedServerUrls = state.expandedServerUrls.intersect(urls),
            )
        }
    }

    /**
     * Starts a coroutine merging the repository's status flow for [url]
     * into the matching [McpServerSnapshot]. The tool list itself is
     * updated by [fetchToolsAndUpdate], so this observer is concerned
     * only with the status field. Returns the [Job] so the caller can
     * cancel it when the URL is removed.
     */
    private fun observeServer(url: String): Job = mcpServerRepository.observeConnectionStatus(serverUrl = url)
        .onEach { status -> updateServerStatus(url = url, status = status) }
        .launchIn(viewModelScope)

    /**
     * Launches a `fetchToolList` for [serverUrl] and merges the result
     * into the snapshot list. Success updates the `tools` field; failure
     * is observed indirectly via the status flow (the repository emits
     * [McpConnectionStatus.Error] on the same code path).
     */
    private fun fetchToolsAndUpdate(serverUrl: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            val tools = mcpServerRepository.fetchToolList(serverUrl = serverUrl, forceRefresh = forceRefresh)
                .getOrElse { return@launch }
            _uiState.update { state ->
                state.copy(
                    mcpServers = state.mcpServers.map { snapshot ->
                        if (snapshot.url == serverUrl) snapshot.copy(tools = tools) else snapshot
                    },
                )
            }
        }
    }

    /** Folds a status emission into the snapshot identified by [url]. */
    private fun updateServerStatus(url: String, status: McpConnectionStatus) {
        _uiState.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map { snapshot ->
                    if (snapshot.url == url) snapshot.copy(status = status) else snapshot
                },
            )
        }
    }

    /** Updates the text input field for a new MCP server URL. */
    fun onMcpUrlInputChanged(url: String) {
        _uiState.update { it.copy(newMcpUrlInput = url) }
    }

    /**
     * Persists the currently entered URL and triggers an immediate
     * `tools/list` fetch for it. The status observer launched by
     * [reconcileServerSet] will pick up the result and surface it in
     * [uiState].
     */
    fun addMcpServer() {
        val url = _uiState.value.newMcpUrlInput.trim()
        if (url.isNotBlank()) {
            viewModelScope.launch {
                settingsRepository.addMcpServerUrl(url = url)
                _uiState.update { it.copy(newMcpUrlInput = "") }
            }
        }
    }

    /** Removes an MCP server URL and disconnects the underlying client. */
    fun removeMcpServer(url: String) {
        viewModelScope.launch {
            settingsRepository.removeMcpServerUrl(url = url)
        }
    }

    /** Toggles whether [serverUrl]'s tool list is expanded in the UI. */
    fun toggleServerExpanded(serverUrl: String) {
        _uiState.update { state ->
            val current = state.expandedServerUrls
            val next = if (serverUrl in current) current - serverUrl else current + serverUrl
            state.copy(expandedServerUrls = next)
        }
    }

    /**
     * Re-fetches the tool list for [serverUrl] bypassing the repository's
     * 5-minute cache. Wired to the trailing refresh icon in
     * `McpServerRow`.
     */
    fun refreshServer(serverUrl: String) {
        fetchToolsAndUpdate(serverUrl = serverUrl, forceRefresh = true)
    }

    /** Toggles the enabled/disabled state of a local AppFunction. */
    fun toggleLocalTool(toolName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.disabledAppFunctions.toMutableSet()
            if (isEnabled) current.remove(toolName) else current.add(toolName)
            settingsRepository.setDisabledAppFunctions(functions = current)
        }
    }

    /** Toggles the enabled/disabled state of an MCP tool (`McpTool.id`). */
    fun toggleMcpTool(toolId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.disabledMcpTools.toMutableSet()
            if (isEnabled) current.remove(toolId) else current.add(toolId)
            settingsRepository.setDisabledMcpTools(toolIds = current)
        }
    }

    /**
     * Resolves an MCP tool by its stable id (matches
     * `McpServerRepositoryImpl.mcpToolId`). Returns `null` if no server
     * in [uiState] currently advertises a tool with this id (e.g. the
     * server was removed since navigation, or the cache is empty).
     */
    fun findMcpTool(toolId: String): McpTool? {
        val servers = _uiState.value.mcpServers
        for (server in servers) {
            server.tools.firstOrNull { it.id == toolId }?.let { return it }
        }
        return null
    }
}
