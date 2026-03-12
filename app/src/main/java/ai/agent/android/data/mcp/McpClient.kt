package ai.agent.android.data.mcp

import ai.agent.android.domain.models.AgentTool

interface McpClient {
    suspend fun connect(url: String)
    suspend fun disconnect()
    suspend fun getTools(): List<AgentTool>
    suspend fun executeTool(name: String, arguments: String): String
}

interface McpClientFactory {
    fun create(): McpClient
}