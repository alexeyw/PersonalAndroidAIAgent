package ai.agent.android.data.mcp

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpServerConfig

/**
 * Interface representing a client for the Model Context Protocol (MCP).
 * This client is responsible for establishing a connection with an MCP server,
 * discovering available tools, and executing them.
 */
interface McpClient {
    /**
     * Connects to the MCP server described by [config]. Implementations honour
     * [McpServerConfig.headers] (typically `Authorization: Bearer …`) and the
     * declared transport.
     *
     * Implementations may downgrade an unsupported transport to a viable default
     * (Koog 0.8 only ships SSE, so `STREAMABLE_HTTP` falls back to SSE today),
     * but must never silently drop auth headers.
     */
    suspend fun connect(config: McpServerConfig)

    /**
     * Disconnects from the currently connected MCP server and releases any held resources.
     */
    suspend fun disconnect()

    /**
     * Retrieves the list of available tools from the connected MCP server.
     *
     * @return A list of [AgentTool] objects representing the tools exposed by the server.
     */
    suspend fun getTools(): List<AgentTool>

    /**
     * Executes a specific tool on the MCP server with the provided arguments.
     *
     * @param name The name of the tool to execute.
     * @param arguments A JSON string representing the arguments required by the tool.
     * @return A string containing the result of the tool's execution.
     */
    suspend fun executeTool(name: String, arguments: String): String
}

/**
 * Factory interface for creating instances of [McpClient].
 * Useful for dependency injection where multiple clients might need to be created.
 */
interface McpClientFactory {
    /**
     * Creates and returns a new instance of [McpClient].
     *
     * @return A new [McpClient] instance.
     */
    fun create(): McpClient
}
