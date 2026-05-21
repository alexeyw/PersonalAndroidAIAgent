package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool

/**
 * UI state for the Tools screen.
 *
 * @property mcpServers per-server snapshot for the MCP section: URL,
 * connection status, and the tool list (when the connection succeeded).
 * @property newMcpUrlInput current text in the inline "Add MCP server URL"
 * form. Persisted in the ViewModel so the input survives configuration
 * changes; the catalog passes it back through [ToolsUiState] when the
 * form is open.
 * @property disabledAppFunctions ids of locally registered AppFunctions
 * the user has paused (stored in `SettingsRepository.disabledAppFunctions`).
 * @property disabledMcpTools ids (`McpTool.id`) of MCP tools the user has
 * paused (stored in `SettingsRepository.disabledMcpTools`).
 * @property localTools all AppFunctions discovered on-device by
 * `ToolRepository.getAllLocalTools`. Used both to render the
 * "Built-in" section and to source `AgentTool.parameters` for the
 * detail screen's schema preview.
 * @property expandedServerUrls server URLs currently expanded in the
 * UI; used to drive the chevron and the nested tool-row list. Defaults
 * to empty (every server starts collapsed).
 */
data class ToolsUiState(
    val mcpServers: List<McpServerSnapshot> = emptyList(),
    val newMcpUrlInput: String = "",
    val disabledAppFunctions: Set<String> = emptySet(),
    val disabledMcpTools: Set<String> = emptySet(),
    val localTools: List<AgentTool> = emptyList(),
    val expandedServerUrls: Set<String> = emptySet(),
)

/**
 * Per-server slice surfaced to the UI.
 *
 * @property url server URL (matches the entry in
 * `SettingsRepository.mcpServerUrls`).
 * @property status current connection status from
 * `McpServerRepository.observeConnectionStatus`.
 * @property tools tools advertised by the server on its last successful
 * `tools/list` fetch. Empty while [status] is
 * [McpConnectionStatus.Connecting] or [McpConnectionStatus.Error].
 */
data class McpServerSnapshot(val url: String, val status: McpConnectionStatus, val tools: List<McpTool>)
