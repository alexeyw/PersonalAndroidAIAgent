package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.ToolRepository
import javax.inject.Inject

/**
 * Implementation of [ToolRepository] that delegates to [McpClient].
 */
class ToolRepositoryImpl @Inject constructor(
    private val mcpClient: McpClient
) : ToolRepository {

    override suspend fun getAvailableTools(): List<AgentTool> {
        return mcpClient.getTools()
    }

    override suspend fun executeTool(name: String, arguments: String): String {
        return mcpClient.executeTool(name, arguments)
    }
}