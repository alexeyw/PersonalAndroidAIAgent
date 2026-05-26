@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockToolsViewModel` factory function (primary
// export) and its sibling `ToolsMockHandles` data class.

package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpAuth
import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpServerConfig
import ai.agent.android.domain.models.McpTool
import ai.agent.android.domain.models.McpTransport
import ai.agent.android.domain.models.ToolRisk
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of [ToolsViewModel.uiState] used by androidTest scenarios
 * to drive the Tools surface through connection-state and toggle
 * transitions without re-stubbing the mock between phases.
 */
internal class ToolsMockHandles(val uiStateFlow: MutableStateFlow<ToolsUiState>)

/**
 * Builds a relaxed [ToolsViewModel] mock with [uiState] stubbed to a
 * deterministic starting value. Default state is empty; callers pass an
 * explicit [initialUiState] for non-trivial scenarios.
 */
internal fun mockToolsViewModel(
    initialUiState: ToolsUiState = ToolsUiState(),
): Pair<ToolsViewModel, ToolsMockHandles> {
    val uiStateFlow = MutableStateFlow(initialUiState)
    val vm = mockk<ToolsViewModel>(relaxed = true)
    every { vm.uiState } returns uiStateFlow
    val handles = ToolsMockHandles(uiStateFlow = uiStateFlow)
    return vm to handles
}

/** Builds an [AgentTool] sample with the given risk classification. */
internal fun sampleAgentTool(
    name: String,
    description: String = "$name description",
    risk: ToolRisk = ToolRisk.READ_ONLY,
): AgentTool = AgentTool(
    name = name,
    description = description,
    parameters = "{}",
    risk = risk,
)

/** Builds an [McpServerSnapshot] for one MCP server in a given connection state. */
internal fun sampleServerSnapshot(
    url: String = "https://mcp.example.com",
    displayName: String = "Example MCP",
    status: McpConnectionStatus = McpConnectionStatus.Connecting,
    tools: List<McpTool> = emptyList(),
): McpServerSnapshot = McpServerSnapshot(
    config = McpServerConfig(
        url = url,
        name = displayName,
        transport = McpTransport.SSE,
        auth = McpAuth.None,
        headers = emptyMap(),
    ),
    status = status,
    tools = tools,
)

/** Builds one MCP tool sample for inclusion in an [McpServerSnapshot]. */
internal fun sampleMcpTool(
    id: String,
    serverUrl: String = "https://mcp.example.com",
    name: String = "remote_tool",
    description: String = "Remote MCP tool",
    risk: ToolRisk? = null,
    inputSchemaJson: String = "{\"type\":\"object\"}",
): McpTool = McpTool(
    id = id,
    serverUrl = serverUrl,
    name = name,
    description = description,
    inputSchemaJson = inputSchemaJson,
    risk = risk,
)
