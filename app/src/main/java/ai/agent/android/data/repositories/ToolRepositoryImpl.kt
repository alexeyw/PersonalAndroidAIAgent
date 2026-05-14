package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.data.tools.local.SearchTool
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.LocalToolExecutor
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Implementation of [ToolRepository] that manages multiple [McpClient] connections
 * and local AppFunctions based on application settings.
 *
 * @property localToolExecutors Hilt multibinding map keyed by tool name. Each entry
 * implements one of the built-in agent tools (e.g. `schedule_task`, `delegate_task`,
 * `search_tool`). New built-ins are added by registering another implementation in DI;
 * an unknown name now fails fast instead of returning a fake success string.
 */
class ToolRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mcpClientFactory: McpClientFactory,
    private val localAppFunctionManager: LocalAppFunctionManager,
    private val apiKeyRepository: ApiKeyRepository,
    private val searchTool: SearchTool,
    private val localToolExecutors: Map<String, @JvmSuppressWildcards LocalToolExecutor>,
) : ToolRepository {

    private val mcpClients = ConcurrentHashMap<String, McpClient>()

    private suspend fun getBuiltinTools(): List<AgentTool> {
        val availableModels = mutableListOf<CloudProvider>()
        if (!apiKeyRepository.getOpenAIKey().firstOrNull().isNullOrBlank()) availableModels.add(CloudProvider.OPENAI)
        if (!apiKeyRepository.getAnthropicKey().firstOrNull().isNullOrBlank()) {
            availableModels.add(CloudProvider.ANTHROPIC)
        }
        if (!apiKeyRepository.getGoogleKey().firstOrNull().isNullOrBlank()) availableModels.add(CloudProvider.GOOGLE)
        if (!apiKeyRepository.getDeepSeekKey().firstOrNull().isNullOrBlank()) {
            availableModels.add(CloudProvider.DEEPSEEK)
        }
        if (!apiKeyRepository.getOllamaBaseUrl().firstOrNull().isNullOrBlank()) {
            availableModels.add(CloudProvider.OLLAMA)
        }

        val scheduleTool = AgentTool(
            name = "schedule_task",
            description = "Schedules a task to be executed by the agent in the background. " +
                "intervalHours: >0 for periodic, 0 for one-time. " +
                "delayMinutes: >0 for delayed execution.",
            parameters = """
                {
                  "type": "object",
                  "properties": {
                    "prompt": { "type": "string", "description": "The prompt or task description" },
                    "intervalHours": { "type": "integer", "description": "Interval in hours for periodic tasks. Default 0." },
                    "delayMinutes": { "type": "integer", "description": "Delay in minutes for one-time tasks. Default 0." }
                  },
                  "required": ["prompt"]
                }
            """.trimIndent(),
            risk = ToolRisk.SENSITIVE,
        )

        val baseTools = mutableListOf(
            scheduleTool,
            searchTool.asAgentTool().copy(risk = ToolRisk.READ_ONLY),
        )

        if (availableModels.isEmpty()) {
            return baseTools
        }

        val modelsString = availableModels.joinToString(", ") { it.id }
        val defaultModel = availableModels.first().id
        val delegateTool = AgentTool(
            name = "delegate_task",
            description = "Delegates a complex or specialized task to an external LLM and saves the " +
                "result to memory. ONLY use this tool if you need cloud reasoning.",
            parameters = """
                {
                  "type": "object",
                  "properties": {
                    "taskDescription": { "type": "string", "description": "A detailed explanation of the task to be delegated" },
                    "targetModel": { "type": "string", "description": "The external model to use. MUST be one of: $modelsString. Default is $defaultModel." }
                  },
                  "required": ["taskDescription"]
                }
            """.trimIndent(),
            risk = ToolRisk.SENSITIVE,
        )

        baseTools.add(delegateTool)
        return baseTools
    }

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

    /**
     * Retrieves all locally available tools — built-in tools first (stable ordering for
     * prompt engineering), then AppFunctions discovered via
     * [LocalAppFunctionManager.getAvailableFunctions].
     *
     * Built-ins always win on name collisions: a discovered AppFunction whose id matches
     * a built-in (`schedule_task`, `search_tool`, `delegate_task`) is dropped with a
     * `Timber.w` to preserve the executor mapping in [executeTool] and the risk classification
     * in `getRisk`. This keeps the deterministic built-in path intact even when the host
     * device exposes AppFunctions advertising the same identifier.
     *
     * @return A list of [AgentTool] representing the local tools available.
     */
    override suspend fun getAllLocalTools(): List<AgentTool> {
        val builtins = getBuiltinTools()
        val builtinNames = builtins.mapTo(mutableSetOf()) { it.name }
        val appFunctions = localAppFunctionManager.getAvailableFunctions().filter { tool ->
            if (tool.name in builtinNames) {
                Timber.w("AppFunction %s collides with built-in tool; built-in wins", tool.name)
                false
            } else {
                true
            }
        }
        return builtins + appFunctions
    }

    /**
     * Retrieves all available tools, including both local tools (not disabled) and tools
     * fetched from connected MCP servers.
     *
     * The `disabledAppFunctions` setting filters the merged local catalogue — it gates
     * both built-ins and discovered AppFunctions by name.
     *
     * @return A list of [AgentTool] representing all tools currently available to the agent.
     */
    override suspend fun getAvailableTools(): List<AgentTool> {
        syncMcpClients()
        val disabled = settingsRepository.disabledAppFunctions.first()
        val availableLocal = getAllLocalTools().filter { it.name !in disabled }

        val mcpTools = mcpClients.values.flatMap { client ->
            try {
                client.getTools()
            } catch (e: Exception) {
                emptyList()
            }
        }

        return availableLocal + mcpTools
    }

    /**
     * Executes a tool by its name with the given arguments.
     *
     * Dispatch order:
     *  1. Built-in tools — handled by a [LocalToolExecutor] registered in DI.
     *  2. AppFunctions discovered via [LocalAppFunctionManager.invokeByName] — the manager
     *     owns the end-to-end pipeline (codec encode → `ExecuteAppFunctionRequest` →
     *     system call → codec decode). All Android AppFunctions types stay encapsulated
     *     behind that surface; this method only sees plain `String` in and out.
     *  3. MCP — forwarded to any connected client that advertises the name.
     *
     * @param name The name of the tool to execute.
     * @param arguments A JSON string containing the arguments required by the tool.
     * @return A string representing the result of the tool execution.
     * @throws IllegalArgumentException If the tool is disabled, has no executor registered,
     *   or is not found across active providers.
     * @throws IllegalStateException If a system-level AppFunction call reports a failure
     *   (re-thrown verbatim by [LocalAppFunctionManager.invokeByName]).
     */
    override suspend fun executeTool(name: String, arguments: String): String {
        val builtinTools = getBuiltinTools()
        val disabled = settingsRepository.disabledAppFunctions.first()
        if (builtinTools.any { it.name == name }) {
            if (name in disabled) {
                throw IllegalArgumentException("Tool $name is disabled")
            }

            val executor = localToolExecutors[name]
                ?: throw IllegalArgumentException("Local tool $name has no executor registered")
            return executor.execute(arguments)
        }

        val builtinNames = builtinTools.mapTo(mutableSetOf()) { it.name }
        if (name !in builtinNames && localAppFunctionManager.isDiscovered(name)) {
            if (name in disabled) {
                throw IllegalArgumentException("Tool $name is disabled")
            }
            return localAppFunctionManager.invokeByName(name, arguments)
        }

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

        throw IllegalArgumentException("Tool $name not found across active providers")
    }

    /**
     * Resolves the effective [ToolRisk] for a tool by name. See [ToolRepository.getRisk]
     * KDoc for the resolution contract.
     *
     * The lookup intentionally does **not** cache discovery / settings results: AppFunction
     * availability and risk overrides are user-controlled and must reflect the latest
     * device state on every call. The HITL gate executes this exactly once per tool
     * invocation, so the extra Flow read is not on a hot path.
     */
    override suspend fun getRisk(toolName: String): ToolRisk {
        val builtinRisk = getBuiltinTools().firstOrNull { it.name == toolName }?.risk
        if (builtinRisk != null) {
            return builtinRisk
        }

        if (localAppFunctionManager.isDiscovered(toolName)) {
            val overrides = settingsRepository.appFunctionRiskOverrides.first()
            return overrides[toolName] ?: ToolRisk.SENSITIVE
        }

        syncMcpClients()
        for (client in mcpClients.values) {
            val isMcpTool = runCatching { client.getTools().any { it.name == toolName } }.getOrDefault(false)
            if (isMcpTool) {
                return ToolRisk.SENSITIVE
            }
        }

        throw IllegalArgumentException("Unknown tool: $toolName")
    }
}
