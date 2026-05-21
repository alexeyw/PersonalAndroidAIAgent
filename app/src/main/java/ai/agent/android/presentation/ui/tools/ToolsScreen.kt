package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool
import ai.agent.android.domain.models.ToolRisk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.BuiltInToolRisk
import app.knotwork.design.screens.tools.BuiltInToolRow
import app.knotwork.design.screens.tools.McpConnectionState
import app.knotwork.design.screens.tools.McpServerRow
import app.knotwork.design.screens.tools.McpToolEntry
import app.knotwork.design.screens.tools.ToolsCallbacks
import app.knotwork.design.screens.tools.ToolsContent
import app.knotwork.design.screens.tools.ToolsViewState
import app.knotwork.design.screens.tools.ToolsVisualState

/**
 * Tools screen — two-section list (built-in / MCP). The add and edit
 * affordances open a dedicated full-screen `McpServerConfigScreen`
 * instead of an inline form, so the tall configuration body (URL +
 * Display name + Transport + Headers) has room to breathe.
 */
@Suppress("UnusedParameter") // onBack kept for nav-graph compatibility.
@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenToolDetail: (String) -> Unit = {},
    onAddMcpServer: () -> Unit = {},
    onEditMcpServer: (originalUrl: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val builtInTools by remember(uiState) {
        derivedStateOf {
            uiState.localTools.map { tool ->
                BuiltInToolRow(
                    id = tool.name,
                    name = tool.name.toFriendlyToolName(),
                    description = tool.description,
                    risk = (tool.risk ?: ToolRisk.READ_ONLY).toBuiltInToolRisk(),
                    enabled = tool.name !in uiState.disabledAppFunctions,
                )
            }
        }
    }
    val mcpServers by remember(uiState) {
        derivedStateOf {
            uiState.mcpServers.map { snapshot ->
                McpServerRow(
                    id = snapshot.url,
                    url = snapshot.config.displayName,
                    toolCount = snapshot.tools.size,
                    latencyLabel = snapshot.status.toLabel(),
                    state = snapshot.status.toCatalogState(),
                    tools = snapshot.tools.map { it.toEntry(disabled = uiState.disabledMcpTools) },
                    expanded = snapshot.url in uiState.expandedServerUrls,
                )
            }
        }
    }

    val visualState = if (builtInTools.isEmpty() && mcpServers.isEmpty()) {
        ToolsVisualState.Empty
    } else {
        ToolsVisualState.Default
    }

    val viewState = ToolsViewState(
        visualState = visualState,
        builtInTools = builtInTools,
        mcpServers = mcpServers,
    )

    val callbacks = ToolsCallbacks(
        onToolToggle = { id, enabled -> viewModel.toggleLocalTool(toolName = id, isEnabled = enabled) },
        onToolClick = onOpenToolDetail,
        onServerRemove = { serverId -> viewModel.removeMcpServer(url = serverId) },
        onServerEdit = onEditMcpServer,
        onServerExpandToggle = { serverId -> viewModel.toggleServerExpanded(serverUrl = serverId) },
        onServerRefresh = { serverId -> viewModel.refreshServer(serverUrl = serverId) },
        onMcpToolToggle = { id, enabled -> viewModel.toggleMcpTool(toolId = id, isEnabled = enabled) },
        onMcpToolClick = onOpenToolDetail,
        onAddServerOpen = onAddMcpServer,
        onErrorRetry = { /* no-op until ToolRepository surfaces a discovery error. */ },
        onOpenDrawer = { /* drawer ships post-v0.1. */ },
        onTopOverflow = { /* top overflow ships post-v0.1. */ },
    )

    ToolsContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = TOOLS_ROOT_TEST_TAG),
    )
}

private fun ToolRisk.toBuiltInToolRisk(): BuiltInToolRisk = when (this) {
    ToolRisk.READ_ONLY -> BuiltInToolRisk.ReadOnly
    ToolRisk.SENSITIVE -> BuiltInToolRisk.Sensitive
    ToolRisk.DESTRUCTIVE -> BuiltInToolRisk.Destructive
}

private fun McpConnectionStatus.toCatalogState(): McpConnectionState = when (this) {
    McpConnectionStatus.Connecting -> McpConnectionState.Syncing
    McpConnectionStatus.Connected -> McpConnectionState.Connected
    is McpConnectionStatus.Error -> McpConnectionState.Error
}

private fun McpConnectionStatus.toLabel(): String = when (this) {
    McpConnectionStatus.Connecting -> "connecting…"
    McpConnectionStatus.Connected -> "ok"
    is McpConnectionStatus.Error -> reason
}

/**
 * Projects an [McpTool] to the catalog's [McpToolEntry] for rendering as
 * a nested row underneath the server header.
 */
private fun McpTool.toEntry(disabled: Set<String>): McpToolEntry = McpToolEntry(
    id = id,
    name = name,
    description = description,
    risk = (risk ?: ToolRisk.SENSITIVE).toBuiltInToolRisk(),
    enabled = id !in disabled,
)

/**
 * Trims AppFunction-shaped tool ids (`<pkg>/<FQN>#invoke`) down to the
 * simple class name so the list row reads at a glance.
 */
private fun String.toFriendlyToolName(): String {
    val afterSlash = substringAfterLast(delimiter = "/")
    val beforeHash = afterSlash.substringBefore(delimiter = "#")
    val simple = beforeHash.substringAfterLast(delimiter = ".")
    return simple.ifBlank { this }
}

/** TestTag applied to the tools screen root. */
internal const val TOOLS_ROOT_TEST_TAG = "tools_screen_root"
