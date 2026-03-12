package ai.agent.android.presentation.ui.tools

data class ToolsUiState(
    val mcpServers: List<String> = emptyList(),
    val newMcpUrlInput: String = "",
    val disabledAppFunctions: Set<String> = emptySet(),
    val localTools: List<String> = listOf("get_system_time", "set_alarm")
)
