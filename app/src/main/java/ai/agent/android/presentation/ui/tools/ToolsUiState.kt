package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool

data class ToolsUiState(
    val mcpServers: List<String> = emptyList(),
    val newMcpUrlInput: String = "",
    val disabledAppFunctions: Set<String> = emptySet(),
    val localTools: List<AgentTool> = emptyList(),
)
