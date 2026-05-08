package ai.agent.android.data.mcp

import ai.agent.android.domain.models.AgentTool
import ai.koog.agents.core.tools.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * Concrete implementation of [McpClient] using the Koog framework's MCP tools.
 * It manages the underlying Ktor HttpClient and the Koog ToolRegistry.
 */
@OptIn(ai.koog.agents.core.tools.annotations.InternalAgentToolsApi::class)
class KoogMcpClient : McpClient {
    private var registry: ToolRegistry? = null
    // The HTTP client is recreated on every [connect] so the same client instance can be
    // disconnected and reconnected cleanly. Holding a single client across disconnect would
    // leave it in a closed state, and a subsequent [connect] would immediately fail when the
    // underlying engine is reused.
    private var httpClient: HttpClient? = null
    private val serializer = KotlinxSerializer(Json { ignoreUnknownKeys = true })

    /**
     * Connects to the MCP server at the specified URL using the Koog MCP transport.
     *
     * Calling [connect] more than once on the same instance is supported (e.g. reconnect
     * or repoint scenarios): any previously-attached `HttpClient` is closed before a
     * fresh one is installed so its socket pool and engine threads do not leak until
     * process teardown.
     *
     * @param url The endpoint URL of the MCP server.
     */
    override suspend fun connect(url: String) {
        withContext(Dispatchers.IO) {
            // Drop any previous client+registry pair before reattaching. Without this,
            // a second connect() on the same instance would silently leak the prior
            // HttpClient (and its underlying engine threads/sockets).
            httpClient?.close()
            registry = null
            val client = HttpClient().also { httpClient = it }
            val transport = McpToolRegistryProvider.defaultSseTransport(url, client)
            val serverInfo = McpServerInfo(url = url, command = "")
            registry = McpToolRegistryProvider.fromTransport(transport, serverInfo)
        }
    }

    /**
     * Disconnects from the current MCP server, clearing the registry and closing the HTTP client.
     * The client is nulled out so that a subsequent [connect] creates a fresh instance.
     */
    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            registry = null
            httpClient?.close()
            httpClient = null
        }
    }

    /**
     * Retrieves the list of available tools from the connected Koog ToolRegistry.
     * Maps the Koog tool descriptors to the domain-specific [AgentTool] models.
     *
     * @return A list of [AgentTool] objects, or an empty list if not connected.
     */
    override suspend fun getTools(): List<AgentTool> {
        return withContext(Dispatchers.IO) {
            registry?.tools?.map { tool ->
                AgentTool(
                    name = tool.name,
                    description = tool.descriptor.description,
                    parameters = run {
                        val root = JSONObject()
                        root.put("type", "object")
                        val props = JSONObject()
                        val required = JSONArray()
                        tool.descriptor.requiredParameters.forEach { param ->
                            props.put(param.name, JSONObject().put("type", "string"))
                            required.put(param.name)
                        }
                        tool.descriptor.optionalParameters.forEach { param ->
                            props.put(param.name, JSONObject().put("type", "string"))
                        }
                        root.put("properties", props)
                        if (required.length() > 0) root.put("required", required)
                        root.toString()
                    }
                )
            } ?: emptyList()
        }
    }

    /**
     * Executes a specific tool by name from the Koog ToolRegistry.
     * Parses the JSON arguments and uses the Koog serializer to execute and format the result.
     *
     * @param name The name of the tool to execute.
     * @param arguments A JSON string representing the arguments.
     * @return A string containing the serialized result of the execution.
     * @throws IllegalArgumentException if the tool is not found.
     */
    override suspend fun executeTool(name: String, arguments: String): String {
        return withContext(Dispatchers.IO) {
            val tool = registry?.getToolOrNull(name) 
                ?: throw IllegalArgumentException("Tool $name not found")
            
            val kotlinxJsonArgs = Json.parseToJsonElement(arguments).jsonObject
            val koogJsonArgs = kotlinxJsonArgs.toKoogJSONObject()
            val args = tool.decodeArgs(koogJsonArgs, serializer)
            
            val result = tool.executeUnsafe(args!!)
            tool.encodeResultToStringUnsafe(result!!, serializer)
        }
    }
}

/**
 * Factory class for creating [KoogMcpClient] instances.
 * Injected via Hilt for dependency management.
 */
class KoogMcpClientFactory @Inject constructor() : McpClientFactory {
    /**
     * Creates a new instance of [KoogMcpClient].
     *
     * @return A new [McpClient] implementation.
     */
    override fun create(): McpClient {
        return KoogMcpClient()
    }
}