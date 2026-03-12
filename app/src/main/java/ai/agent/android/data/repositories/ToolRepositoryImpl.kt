package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Implementation of [ToolRepository] that manages multiple [McpClient] connections
 * and local AppFunctions based on application settings.
 */
class ToolRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mcpClientFactory: McpClientFactory,
    private val localAppFunctionManager: LocalAppFunctionManager
) : ToolRepository {

    private val mcpClients = mutableMapOf<String, McpClient>()

    private suspend fun syncMcpClients() {
        val urls = settingsRepository.mcpServerUrls.first()
        val toRemove = mcpClients.keys - urls
        val toAdd = urls - mcpClients.keys

        toRemove.forEach { url ->
            try {
                mcpClients[url]?.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
            mcpClients.remove(url)
        }

        toAdd.forEach { url ->
            val client = mcpClientFactory.create()
            try {
                client.connect(url)
                mcpClients[url] = client
            } catch (e: Exception) {
                // Client failed to connect, we don't add it to the active pool
            }
        }
    }


    override suspend fun getAllLocalTools(): List<AgentTool> {
        return localAppFunctionManager.getAvailableFunctions()
    }

    override suspend fun getAvailableTools(): List<AgentTool> {
        syncMcpClients()
        val disabled = settingsRepository.disabledAppFunctions.first()
        val localTools = localAppFunctionManager.getAvailableFunctions()
        val availableLocal = localTools.filter { it.name !in disabled }
        
        val mcpTools = mcpClients.values.flatMap { client ->
            try {
                client.getTools()
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        return availableLocal + mcpTools
    }

    override suspend fun executeTool(name: String, arguments: String): String {
        val localTools = localAppFunctionManager.getAvailableFunctions()
        // Check if the tool is a known local tool and is not disabled
        val disabled = settingsRepository.disabledAppFunctions.first()
        if (localTools.any { it.name == name }) {
            if (name in disabled) {
                throw IllegalArgumentException("Tool \$name is disabled")
            }
            return "Local tool executed: \$name with \$arguments" // Dummy implementation
        }

        // Ensure MCP clients are synced before searching for the tool
        syncMcpClients()

        for (client in mcpClients.values) {
            try {
                val tools = client.getTools()
                if (tools.any { it.name == name }) {
                    return client.executeTool(name, arguments)
                }
            } catch (e: Exception) {
                // Error querying or executing on this client, try the next one
            }
        }
        
        throw IllegalArgumentException("Tool \$name not found across active providers")
    }
}