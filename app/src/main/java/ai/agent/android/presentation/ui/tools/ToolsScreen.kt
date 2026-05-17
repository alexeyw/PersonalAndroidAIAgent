package ai.agent.android.presentation.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.McpConnectionState
import app.knotwork.design.screens.tools.ToolRowState
import app.knotwork.design.screens.tools.ToolsCallbacks
import app.knotwork.design.screens.tools.ToolsContent
import app.knotwork.design.screens.tools.ToolsSectionBlock
import app.knotwork.design.screens.tools.ToolsViewState
import app.knotwork.design.screens.tools.ToolsVisualState

/**
 * Tools screen — Phase 21 / Task 10 rewrite.
 *
 * Renders the catalog `ToolsContent` driven by [ToolsViewModel]. Local
 * AppFunctions live under a synthetic "Local tools" section; each MCP
 * server URL surfaces as its own collapsible section. Per-server tool
 * discovery is not wired yet (MCP tool list fetching ships post-v0.1) —
 * rows under an MCP section are empty in v0.1, but the section chrome
 * already renders status + reconnect / remove affordances.
 */
@Suppress("UnusedParameter") // onBack retained for nav-graph stability.
@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenAddMcpServer: () -> Unit = {},
    onOpenToolDetail: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val sections by remember(uiState) {
        derivedStateOf {
            buildList {
                add(
                    ToolsSectionBlock(
                        serverId = ToolsSectionBlock.LOCAL_SERVER_ID,
                        displayName = LOCAL_SECTION_TITLE,
                        subtitle = LOCAL_SECTION_SUBTITLE,
                        connectionState = McpConnectionState.Connected,
                        tools = uiState.localTools.map { tool ->
                            ToolRowState(
                                id = tool.name,
                                // AppFunction-shaped ids look like
                                // `<pkg>/<FQN>#invoke`. Keep the id intact
                                // for routing but show the human-friendly
                                // suffix as the display name.
                                name = tool.name.toFriendlyToolName(),
                                description = tool.description,
                                serverId = ToolsSectionBlock.LOCAL_SERVER_ID,
                                enabled = tool.name !in uiState.disabledAppFunctions,
                            )
                        },
                    ),
                )
                uiState.mcpServers.forEach { url ->
                    add(
                        ToolsSectionBlock(
                            serverId = url,
                            displayName = url.substringAfter(delimiter = "://").substringBefore(delimiter = "/")
                                .ifBlank { url },
                            subtitle = url,
                            connectionState = McpConnectionState.Connected,
                            tools = emptyList(),
                        ),
                    )
                }
            }
        }
    }

    val visualState = if (sections.size == 1 && sections.first().tools.isEmpty()) {
        ToolsVisualState.Empty
    } else {
        ToolsVisualState.Default
    }

    val viewState = ToolsViewState(visualState = visualState, sections = sections)

    val callbacks = ToolsCallbacks(
        onToolToggle = { toolId, enabled -> viewModel.toggleLocalTool(toolName = toolId, isEnabled = enabled) },
        onToolClick = onOpenToolDetail,
        onServerRemove = { serverId ->
            if (serverId != ToolsSectionBlock.LOCAL_SERVER_ID) {
                viewModel.removeMcpServer(url = serverId)
            }
        },
        onServerReconnect = { /* reconnect handshake lands with MCP-tool-list fetching */ },
        onAddMcpServer = onOpenAddMcpServer,
        onLearnAboutMcp = { /* opens external docs once the URL lands */ },
    )

    ToolsContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = TOOLS_ROOT_TEST_TAG),
    )
}

/** Synthetic section title for the local AppFunctions. */
private const val LOCAL_SECTION_TITLE = "Local tools"

/** Synthetic section subtitle for the local AppFunctions. */
private const val LOCAL_SECTION_SUBTITLE = "Built-in AppFunctions running on this device."

/**
 * Trims AppFunction-shaped tool ids (`<pkg>/<FQN>#invoke`) down to the
 * simple class name so the list row reads at a glance. Plain ids (no `/`
 * or `#`) pass through unchanged.
 */
private fun String.toFriendlyToolName(): String {
    val afterSlash = substringAfterLast(delimiter = "/")
    val beforeHash = afterSlash.substringBefore(delimiter = "#")
    val simple = beforeHash.substringAfterLast(delimiter = ".")
    return simple.ifBlank { this }
}

/** TestTag applied to the tools screen root. */
internal const val TOOLS_ROOT_TEST_TAG = "tools_screen_root"
