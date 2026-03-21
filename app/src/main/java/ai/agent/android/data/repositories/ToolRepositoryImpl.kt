package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.ScheduleTaskUseCase
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject

/**
 * Implementation of [ToolRepository] that manages multiple [McpClient] connections
 * and local AppFunctions based on application settings.
 */
class ToolRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mcpClientFactory: McpClientFactory,
    private val localAppFunctionManager: LocalAppFunctionManager,
    private val scheduleTaskUseCase: ScheduleTaskUseCase,
    private val delegateTaskTool: ai.agent.android.data.tools.local.DelegateTaskTool
) : ToolRepository {

    private val mcpClients = mutableMapOf<String, McpClient>()

    private val builtinTools = listOf(
        AgentTool(
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
        ),
        AgentTool(
            name = "delegate_task",
            description = "Delegates a complex or specialized task to a powerful external LLM (e.g., Claude, OpenAI, Gemini) and saves the result to memory.",
            parameters = """
                {
                  "type": "object",
                  "properties": {
                    "taskDescription": { "type": "string", "description": "A detailed explanation of the task to be delegated" },
                    "targetModel": { "type": "string", "description": "The external model to use: anthropic, openai, google, deepseek, ollama. Default is anthropic." }
                  },
                  "required": ["taskDescription"]
                }
            """.trimIndent()
        )
    )

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
        return localAppFunctionManager.getAvailableFunctions() + builtinTools
    }

    override suspend fun getAvailableTools(): List<AgentTool> {
        syncMcpClients()
        val disabled = settingsRepository.disabledAppFunctions.first()
        val localTools = localAppFunctionManager.getAvailableFunctions() + builtinTools
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
        val localTools = localAppFunctionManager.getAvailableFunctions() + builtinTools
        // Check if the tool is a known local tool and is not disabled
        val disabled = settingsRepository.disabledAppFunctions.first()
        if (localTools.any { it.name == name }) {
            if (name in disabled) {
                throw IllegalArgumentException("Tool \$name is disabled")
            }
            
            if (name == "schedule_task") {
                val json = JSONObject(arguments)
                val prompt = json.getString("prompt")
                val intervalHours = if (json.has("intervalHours")) json.getLong("intervalHours") else 0L
                val delayMinutes = if (json.has("delayMinutes")) json.getLong("delayMinutes") else 0L
                return scheduleTaskUseCase(prompt, intervalHours, delayMinutes)
            }
            
            if (name == "delegate_task") {
                val json = JSONObject(arguments)
                val taskDescription = json.getString("taskDescription")
                val targetModel = if (json.has("targetModel")) json.getString("targetModel") else "anthropic"
                return delegateTaskTool.executeDelegation(taskDescription, targetModel)
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