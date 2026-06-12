package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.ToolExecutionContext

/**
 * Strategy interface for executing a single locally-registered agent tool.
 *
 * Decouples `ToolRepositoryImpl.executeTool` from the built-in `schedule_task`,
 * `delegate_task` and `search_tool` cases so the repository carries no per-tool
 * branching. Each implementation is registered into a Hilt multibinding map
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
     * @param context Engine-supplied [ToolExecutionContext] carrying trusted
     *   environment values (e.g. the invoking chat session id). Arguments come
     *   from the LLM and cannot be trusted for identifiers; executors that need
     *   environment identity read it from here. Implementations that do not
     *   care simply ignore the parameter.
     * @return The tool's textual result.
     */
    suspend fun execute(arguments: String, context: ToolExecutionContext = ToolExecutionContext.EMPTY): String
}
