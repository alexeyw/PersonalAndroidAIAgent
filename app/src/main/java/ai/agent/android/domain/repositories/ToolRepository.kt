package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.AgentTool

/**
 * Repository interface for managing and accessing tools available to the AI agent.
 */
interface ToolRepository {
    /**
     * Retrieves a list of all currently available tools.
     *
     * @return A list of [AgentTool] instances.
     */
    suspend fun getAvailableTools(): List<AgentTool>

    /**
     * Retrieves a list of all available local tools (AppFunctions), ignoring their disabled state.
     *
     * @return A list of [AgentTool] instances.
     */
    suspend fun getAllLocalTools(): List<AgentTool>

    
    /**
     * Executes a specific tool by its name with the given arguments.
     *
     * @param name The name of the tool to execute.
     * @param arguments The arguments to pass to the tool, formatted as a JSON string.
     * @return The result of the tool execution as a string.
     */
    suspend fun executeTool(name: String, arguments: String): String
}