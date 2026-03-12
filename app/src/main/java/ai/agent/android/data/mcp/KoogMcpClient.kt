package ai.agent.android.data.mcp

import ai.agent.android.domain.models.AgentTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@OptIn(ai.koog.agents.core.tools.annotations.InternalAgentToolsApi::class)
class KoogMcpClient : McpClient {
    private var registry: ToolRegistry? = null
    private val httpClient = HttpClient()

    override suspend fun connect(url: String) {
        withContext(Dispatchers.IO) {
            val transport = McpToolRegistryProvider.defaultSseTransport(url, httpClient)
            registry = McpToolRegistryProvider.fromTransport(transport)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            registry = null
            httpClient.close()
        }
    }

    override suspend fun getTools(): List<AgentTool> {
        return withContext(Dispatchers.IO) {
            registry?.tools?.map { tool ->
                AgentTool(
                    name = tool.name,
                    description = tool.descriptor.description ?: "",
                    parameters = tool.descriptor.requiredParameters.joinToString { it.name } + 
                                 tool.descriptor.optionalParameters.joinToString { it.name }
                )
            } ?: emptyList()
        }
    }

    override suspend fun executeTool(name: String, arguments: String): String {
        return withContext(Dispatchers.IO) {
            val tool = registry?.getToolOrNull(name) 
                ?: throw IllegalArgumentException("Tool \$name not found")
            
            val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
            val args = tool.decodeArgs(jsonArgs)
            
            val result = tool.executeUnsafe(args!!)
            tool.encodeResultToStringUnsafe(result!!)
        }
    }
}