package ai.agent.android.domain.repositories

/**
 * Strategy interface for executing a single locally-registered agent tool.
 *
 * Replaces the long `if-else` chain previously hard-coded inside
 * `ToolRepositoryImpl.executeTool` for the built-in `schedule_task`, `delegate_task` and
 * `search_tool` cases. Each implementation is registered into a Hilt multibinding map
 * keyed by [toolName]; the repository looks up an executor by name and either invokes it
 * or fails fast with `IllegalArgumentException` instead of silently returning a fake
 * "Local tool executed: …" success string.
 *
 * Adding a new tool now requires only a new implementation plus a `@Binds @IntoMap
 * @StringKey(<name>)` line in the DI module — no edits to `ToolRepositoryImpl`.
 */
interface LocalToolExecutor {
    /** Tool name as exposed to the LLM (matches `AgentTool.name` and the DI map key). */
    val toolName: String

    /**
     * Executes the tool with the provided JSON [arguments] string and returns the
     * serialized result for the agent observation log.
     *
     * @param arguments JSON-encoded arguments matching the tool's parameter schema.
     * @return The tool's textual result.
     */
    suspend fun execute(arguments: String): String
}
