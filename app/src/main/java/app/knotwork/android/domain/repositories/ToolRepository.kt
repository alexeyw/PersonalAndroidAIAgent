package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.ToolExecutionContext
import app.knotwork.android.domain.models.ToolRisk

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
     * @param context Engine-supplied [ToolExecutionContext] with trusted environment
     *   values (invoking session id). Forwarded to built-in [LocalToolExecutor]
     *   strategies; the AppFunction and MCP branches ignore it.
     * @return The result of the tool execution as a string.
     */
    suspend fun executeTool(
        name: String,
        arguments: String,
        context: ToolExecutionContext = ToolExecutionContext.EMPTY,
    ): String

    /**
     * Resolves the effective [ToolRisk] for a tool by its name. This is the single
     * seam consumed by the Human-in-the-Loop gate: the gate must never read
     * `AgentTool.risk` directly so that user-supplied overrides take precedence
     * over whatever the discovery source declared.
     *
     * Resolution order:
     * 1. Built-in tools (`schedule_task`, `search_tool`, `delegate_task`) return
     *    their hard-coded risk constants.
     * 2. Discovered AppFunctions return the user override from
     *    `SettingsRepository.appFunctionRiskOverrides` if set, otherwise
     *    [ToolRisk.SENSITIVE] (we cannot trust the AppFunctionManager metadata
     *    for side-effect signal).
     * 3. MCP tools return a blanket [ToolRisk.SENSITIVE] until a finer-grained
     *    per-server policy is introduced.
     *
     * @param toolName The name of the tool to look up.
     * @return The effective [ToolRisk].
     * @throws IllegalArgumentException if no tool with the given name is known to
     * any of the active sources.
     */
    suspend fun getRisk(toolName: String): ToolRisk
}
