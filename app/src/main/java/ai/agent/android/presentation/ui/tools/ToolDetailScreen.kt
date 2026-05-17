package ai.agent.android.presentation.ui.tools

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

/**
 * Per-tool detail screen — shows the tool name, description, server
 * affiliation, JSON schema preview, and an enable/disable toggle.
 *
 * Phase 21 / Task 10 ships the local-tool path; per-MCP-tool detail
 * lands once the MCP tool-list fetcher is wired.
 *
 * @param toolId stable tool identifier (matches `AgentTool.name`).
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
    val tool = remember(toolId, uiState.localTools) {
        uiState.localTools.firstOrNull { it.name == toolId }
    }
    val enabled by remember(toolId, uiState.disabledAppFunctions) {
        derivedStateOf { toolId !in uiState.disabledAppFunctions }
    }

    val viewState = ToolDetailViewState(
        visualState = ToolDetailVisualState.Default,
        toolName = tool?.name ?: toolId,
        description = tool?.description.orEmpty(),
        serverDisplayName = "Local tool",
        schemaJson = tool?.let { LOCAL_TOOL_SCHEMA_PREVIEW.format(it.name) },
        lastUsed = null,
        enabled = enabled,
    )

    val callbacks = ToolDetailCallbacks(
        onBack = onBack,
        onToggle = { isEnabled -> viewModel.toggleLocalTool(toolName = toolId, isEnabled = isEnabled) },
    )

    ToolDetailContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = TOOL_DETAIL_ROOT_TEST_TAG),
    )
}

/** TestTag applied to the tool-detail screen root. */
internal const val TOOL_DETAIL_ROOT_TEST_TAG = "tool_detail_root"

/**
 * Cheap stand-in JSON schema preview for local tools. MCP tools surface
 * real JSON-Schema fetched from the server once the integration lands.
 */
private const val LOCAL_TOOL_SCHEMA_PREVIEW = """{
  "name": "%s",
  "type": "object",
  "properties": { "...": { "type": "string" } }
}"""
