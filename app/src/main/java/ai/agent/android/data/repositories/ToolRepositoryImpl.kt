package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.LocalToolExecutor
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
    private val searchTool: ai.agent.android.data.tools.local.SearchTool,
    private val localToolExecutors: Map<String, @JvmSuppressWildcards LocalToolExecutor>,
) : ToolRepository {

    private val mcpClients = ConcurrentHashMap<String, McpClient>()

    private suspend fun getBuiltinTools(): List<AgentTool> {
        val availableModels = mutableListOf<String>()
        if (!apiKeyRepository.getOpenAIKey().firstOrNull().isNullOrBlank()) availableModels.add("openai")
        if (!apiKeyRepository.getAnthropicKey().firstOrNull().isNullOrBlank()) availableModels.add("anthropic")
        if (!apiKeyRepository.getGoogleKey().firstOrNull().isNullOrBlank()) availableModels.add("google")
        if (!apiKeyRepository.getDeepSeekKey().firstOrNull().isNullOrBlank()) availableModels.add("deepseek")
        if (!apiKeyRepository.getOllamaBaseUrl().firstOrNull().isNullOrBlank()) availableModels.add("ollama")

        val scheduleTool = AgentTool(
            name = "schedule_task",
            description = "Schedules a task to be executed by the agent in the background. intervalHours: >0 for periodic, 0 for one-time. delayMinutes: >0 for delayed execution.",
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
            """.trimIndent()
        )

        val baseTools = mutableListOf(scheduleTool, searchTool.asAgentTool())

        if (availableModels.isEmpty()) {
            return baseTools
        }

        val modelsString = availableModels.joinToString(", ")
        val defaultModel = availableModels.first()
        val delegateTool = AgentTool(
            name = "delegate_task",
            description = "Delegates a complex or specialized task to an external LLM and saves the result to memory. ONLY use this tool if you need cloud reasoning.",
            parameters = """
                {
                  "type": "object",
                  "properties": {
                    "taskDescription": { "type": "string", "description": "A detailed explanation of the task to be delegated" },
                    "targetModel": { "type": "string", "description": "The external model to use. MUST be one of: $modelsString. Default is $defaultModel." }
                  },
                  "required": ["taskDescription"]
                }
            """.trimIndent()
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
     * Retrieves all locally available tools.
     *
     * AppFunctions discovered via [LocalAppFunctionManager.getAvailableFunctions] are
     * intentionally **not** included until end-to-end execution (building
     * `ExecuteAppFunctionRequest` with typed `AppFunctionData` from the LLM-supplied
     * arguments) is wired up. Advertising them here would let the agent
     * deterministically pick a tool that cannot run, and `executeTool` would have to
     * raise `Local tool $name has no executor registered` — a worse failure mode than
     * simply hiding them from the catalog.
     *
     * @return A list of [AgentTool] representing the local tools available.
     */
    override suspend fun getAllLocalTools(): List<AgentTool> {
        return getBuiltinTools()
    }

    /**
     * Retrieves all available tools, including both local tools (not disabled) and tools
     * fetched from connected MCP servers.
     *
     * See [getAllLocalTools] for the rationale behind excluding AppFunctions from the
     * advertised set.
     *
     * @return A list of [AgentTool] representing all tools currently available to the agent.
     */
    override suspend fun getAvailableTools(): List<AgentTool> {
        syncMcpClients()
        val disabled = settingsRepository.disabledAppFunctions.first()
        val availableLocal = getBuiltinTools().filter { it.name !in disabled }

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
     * The tool is first looked up in built-in local tools (each backed by a registered
     * [LocalToolExecutor]); if not found, the request is forwarded to any MCP server
     * that advertises the name.
     *
     * AppFunctions discovered via [LocalAppFunctionManager] are not handled here yet —
     * see [getAllLocalTools] for the rationale.
     *
     * @param name The name of the tool to execute.
     * @param arguments A JSON string containing the arguments required by the tool.
     * @return A string representing the result of the tool execution.
     * @throws IllegalArgumentException If the tool is disabled, has no executor registered,
     * or is not found across active providers.
     */
    override suspend fun executeTool(name: String, arguments: String): String {
        // Check if the tool is a known built-in local tool and is not disabled
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

        throw IllegalArgumentException("Tool $name not found across active providers")
    }
}
