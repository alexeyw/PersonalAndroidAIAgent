package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool
import ai.agent.android.domain.models.ToolRisk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.AddMcpServerForm
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
 * Tools screen — two-section list: built-in AppFunctions on top with risk
 * pills + Switch toggles; MCP servers below with a per-server status dot,
 * a trailing refresh + delete + expand affordance, and a nested list of
 * the server's MCP tools when expanded.
 */
@Suppress("UnusedParameter") // onBack kept for nav-graph compatibility.
@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenToolDetail: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var addFormOpen by remember { mutableStateOf(false) }
    var addFormUrl by remember { mutableStateOf("") }
    var addFormError by remember { mutableStateOf<String?>(null) }

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
                    url = snapshot.url,
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
        addServerForm = if (addFormOpen) {
            AddMcpServerForm(url = addFormUrl, urlError = addFormError)
        } else {
            null
        },
    )

    val callbacks = ToolsCallbacks(
        onToolToggle = { id, enabled -> viewModel.toggleLocalTool(toolName = id, isEnabled = enabled) },
        onToolClick = onOpenToolDetail,
        onServerRemove = { serverId -> viewModel.removeMcpServer(url = serverId) },
        onServerExpandToggle = { serverId -> viewModel.toggleServerExpanded(serverUrl = serverId) },
        onServerRefresh = { serverId -> viewModel.refreshServer(serverUrl = serverId) },
        onMcpToolToggle = { id, enabled -> viewModel.toggleMcpTool(toolId = id, isEnabled = enabled) },
        onMcpToolClick = onOpenToolDetail,
        onAddServerOpen = {
            addFormOpen = true
            addFormUrl = ""
            addFormError = null
        },
        onAddServerUrlChange = { value ->
            addFormUrl = value
            addFormError = validateMcpUrl(input = value, requireNonEmpty = false)
            viewModel.onMcpUrlInputChanged(url = value)
        },
        onAddServerSubmit = {
            val error = validateMcpUrl(input = addFormUrl, requireNonEmpty = true)
            if (error == null) {
                viewModel.onMcpUrlInputChanged(url = addFormUrl)
                viewModel.addMcpServer()
                addFormOpen = false
                addFormUrl = ""
                addFormError = null
            } else {
                addFormError = error
            }
        },
        onAddServerCancel = {
            addFormOpen = false
            addFormUrl = ""
            addFormError = null
        },
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
 * a nested row underneath the server header. MCP tools without a server-
 * declared risk fall back to [BuiltInToolRisk.Sensitive] — mirroring the
 * blanket policy in `ToolRepository.getRisk`.
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
 * simple class name so the list row reads at a glance. Plain ids
 * (no `/` or `#`) pass through unchanged.
 */
private fun String.toFriendlyToolName(): String {
    val afterSlash = substringAfterLast(delimiter = "/")
    val beforeHash = afterSlash.substringBefore(delimiter = "#")
    val simple = beforeHash.substringAfterLast(delimiter = ".")
    return simple.ifBlank { this }
}

/**
 * Cheap URL validation matching the legacy AddMcpServerScreen rules:
 * the URL must be non-empty (when [requireNonEmpty] is true) and start
 * with `http://`, `https://`, or `mcp://` followed by a host.
 */
private fun validateMcpUrl(input: String, requireNonEmpty: Boolean): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return if (requireNonEmpty) URL_REQUIRED_MESSAGE else null
    }
    val lower = trimmed.lowercase()
    val schemes = listOf("http://", "https://", "mcp://")
    val matchedScheme = schemes.firstOrNull { lower.startsWith(prefix = it) }
        ?: return URL_SCHEME_REQUIRED_MESSAGE
    val afterScheme = trimmed.substring(startIndex = matchedScheme.length)
    if (afterScheme.isEmpty() || afterScheme.startsWith(prefix = "/")) {
        return URL_HOST_REQUIRED_MESSAGE
    }
    return null
}

private const val URL_REQUIRED_MESSAGE = "Enter a server URL."
private const val URL_SCHEME_REQUIRED_MESSAGE = "URL must start with http://, https:// or mcp://."
private const val URL_HOST_REQUIRED_MESSAGE = "URL needs a host name."

/** TestTag applied to the tools screen root. */
internal const val TOOLS_ROOT_TEST_TAG = "tools_screen_root"
