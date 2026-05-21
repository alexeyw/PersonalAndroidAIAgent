package ai.agent.android.presentation.ui.tools

import ai.agent.android.data.repositories.McpServerRepositoryImpl
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.ToolDetailCallbacks
import app.knotwork.design.screens.tools.ToolDetailContent
import app.knotwork.design.screens.tools.ToolDetailViewState
import app.knotwork.design.screens.tools.ToolDetailVisualState
import org.json.JSONException
import org.json.JSONObject

/**
 * Per-tool detail screen.
 *
 * Branches on the [toolId] prefix:
 *
 *  - `mcp:…` → resolves an [ai.agent.android.domain.models.McpTool] from
 *    `ToolsViewModel.findMcpTool`. Renders the server-supplied JSON Schema
 *    verbatim and the enabled-toggle is wired to
 *    `SettingsRepository.disabledMcpTools`.
 *  - anything else → treats the id as an AppFunction name and pulls the
 *    matching `AgentTool` from `ToolsViewModel.uiState.localTools`. The
 *    schema preview now renders the real `AgentTool.parameters` (was a
 *    cosmetic placeholder before this task).
 *
 * @param toolId stable tool identifier; route argument from `AppNavGraph`.
 * @param onBack pop the back stack.
 */
@Composable
fun ToolDetailScreen(
    toolId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMcp = remember(toolId) { toolId.startsWith(McpServerRepositoryImpl.MCP_ID_PREFIX) }

    val viewState = if (isMcp) {
        val mcpTool = remember(toolId, uiState.mcpServers) { viewModel.findMcpTool(toolId = toolId) }
        val enabled by remember(toolId, uiState.disabledMcpTools) {
            derivedStateOf { toolId !in uiState.disabledMcpTools }
        }
        ToolDetailViewState(
            visualState = mcpTool?.inputSchemaJson?.visualStateForSchema() ?: ToolDetailVisualState.Loading,
            toolName = mcpTool?.name ?: toolId,
            description = mcpTool?.description.orEmpty(),
            serverDisplayName = mcpTool?.serverUrl ?: "MCP tool",
            schemaJson = mcpTool?.inputSchemaJson,
            lastUsed = null,
            enabled = enabled,
        )
    } else {
        val tool = remember(toolId, uiState.localTools) {
            uiState.localTools.firstOrNull { it.name == toolId }
        }
        val enabled by remember(toolId, uiState.disabledAppFunctions) {
            derivedStateOf { toolId !in uiState.disabledAppFunctions }
        }
        ToolDetailViewState(
            visualState = tool?.parameters?.visualStateForSchema() ?: ToolDetailVisualState.Default,
            toolName = tool?.name ?: toolId,
            description = tool?.description.orEmpty(),
            serverDisplayName = "Local tool",
            schemaJson = tool?.parameters?.takeIf { it.isNotBlank() } ?: "{}",
            lastUsed = null,
            enabled = enabled,
        )
    }

    val callbacks = ToolDetailCallbacks(
        onBack = onBack,
        onToggle = { isEnabled ->
            if (isMcp) {
                viewModel.toggleMcpTool(toolId = toolId, isEnabled = isEnabled)
            } else {
                viewModel.toggleLocalTool(toolName = toolId, isEnabled = isEnabled)
            }
        },
    )

    ToolDetailContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = TOOL_DETAIL_ROOT_TEST_TAG),
    )
}

/**
 * Decides which [ToolDetailVisualState] applies given a schema string.
 * Blank → `Default` (renders the empty `{}` placeholder); malformed JSON
 * → `SchemaError`; otherwise `Default`. The `Loading` state is reserved
 * for the MCP path while the repository fetch is still in flight.
 */
private fun String.visualStateForSchema(): ToolDetailVisualState {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ToolDetailVisualState.Default
    return try {
        JSONObject(trimmed)
        ToolDetailVisualState.Default
    } catch (_: JSONException) {
        ToolDetailVisualState.SchemaError
    }
}

/** TestTag applied to the tool-detail screen root. */
internal const val TOOL_DETAIL_ROOT_TEST_TAG = "tool_detail_root"
