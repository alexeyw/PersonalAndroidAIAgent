package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val toolRepository: ToolRepository,
    private val settingsRepository: SettingsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val chatRepository: ChatRepository,
) : NodeExecutor {

    private val activeApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        val deferred = activeApprovalDeferreds.remove(sessionId)
        deferred?.complete(isApproved)
    }

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        val toolNameConfig = node.toolName
        if (toolNameConfig.isNullOrBlank()) {
            emit(NodeOutput.Result(NodeExecutionResult(error = "Tool node is missing toolName configuration.")))
            return@flow
        }

        emit(NodeOutput.State(AgentOrchestratorState.Thinking("Analyzing task for tool execution...")))

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is ai.agent.android.domain.models.Result.Error) {
            val errorMsg = "Error loading local model for tool node"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
            return@flow
        }

        val resolvedToolName: String
        val resolvedToolArgs: String

        if (toolNameConfig.equals("auto", ignoreCase = true)) {
            val availableTools = toolRepository.getAvailableTools()
            if (availableTools.isEmpty()) {
                val errorMsg = "No tools available for auto selection"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val toolsDescriptions = availableTools.joinToString("\n\n") {
                "Tool: ${it.name}\nDescription: ${it.description}\nParameters: ${it.parameters}"
            }

            val prompt = """
                You are an AI assistant that selects the best tool for a given task and generates arguments.
                
                AVAILABLE TOOLS:
                $toolsDescriptions
                
                TASK:
                $inputText
                
                INSTRUCTIONS:
                Choose the most appropriate tool to solve the task. 
                Generate strictly valid JSON with two fields: "tool" and "arguments".
                "tool" should be the exact name of the selected tool.
                "arguments" should contain the parameters matching the tool's schema.
                
                JSON OUTPUT: 
            """.trimIndent()

            val responseStream = llmEngine.generateResponseStream(prompt)
            val accumulatedResponse = StringBuilder()
            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                }
            } catch (e: CancellationException) {
                // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
                // would silently swallow cancellation and leave the parent coroutine running.
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").e(e, "Error generating tool selection via LLM")
                val errorMsg = "Error generating tool selection: ${e.message}"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val parsedSelection = parseToolSelection(accumulatedResponse.toString())
            if (parsedSelection == null) {
                val errorMsg = "Failed to parse tool selection JSON from LLM output"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            resolvedToolName = parsedSelection.first
            resolvedToolArgs = parsedSelection.second
        } else {
            val tools = toolRepository.getAvailableTools()
            val selectedTool = tools.find { it.name == toolNameConfig }
            if (selectedTool == null) {
                val errorMsg = "Tool $toolNameConfig not found in available tools"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val prompt = """
                You are an AI assistant that generates arguments for a specific tool.
                
                TOOL: ${selectedTool.name}
                DESCRIPTION: ${selectedTool.description}
                PARAMETERS SCHEMA: ${selectedTool.parameters}
                
                TASK:
                $inputText
                
                INSTRUCTIONS:
                Based on the task description, generate strictly valid JSON for the tool's "arguments" according to its schema.
                Do not wrap in anything else, just the JSON for the arguments. If it's a primitive, output {"tool": "${selectedTool.name}", "arguments": <value>}.
                
                JSON OUTPUT: 
            """.trimIndent()

            val responseStream = llmEngine.generateResponseStream(prompt)
            val accumulatedResponse = StringBuilder()
            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                }
            } catch (e: CancellationException) {
                // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
                // would silently swallow cancellation and leave the parent coroutine running.
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").e(e, "Error generating tool arguments via LLM")
                val errorMsg = "Error generating tool arguments: ${e.message}"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val parsedSelection = parseToolSelection(accumulatedResponse.toString())
            if (parsedSelection != null && parsedSelection.first == toolNameConfig) {
                resolvedToolName = parsedSelection.first
                resolvedToolArgs = parsedSelection.second
            } else {
                resolvedToolName = toolNameConfig
                resolvedToolArgs =
                    parseToolArguments(accumulatedResponse.toString()) ?: accumulatedResponse.toString().trim()
            }
        }

        val requiresUserConfirmation = settingsRepository.requiresUserConfirmation.first()
        var isApproved = true

        if (requiresUserConfirmation) {
            emit(NodeOutput.State(AgentOrchestratorState.WaitingForApproval(resolvedToolName, resolvedToolArgs)))
            approvalNotifier.sendApprovalRequest(sessionId, resolvedToolName, resolvedToolArgs)

            // Register deferred before any suspension point so a fast approval is not dropped
            val deferred = CompletableDeferred<Boolean>()
            activeApprovalDeferreds[sessionId] = deferred
            val timeoutMs = settingsRepository.toolCallTimeoutMs.first()
            isApproved = try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                activeApprovalDeferreds.remove(sessionId)
                Timber.tag("PipelineDebug").w("Approval timed out for session: $sessionId")
                emit(NodeOutput.State(AgentOrchestratorState.Error("Approval request timed out")))
                emit(NodeOutput.Result(NodeExecutionResult(error = "Approval request timed out")))
                return@flow
            }

            if (!isApproved) {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "User denied execution of tool: $resolvedToolName",
                        timestamp = System.currentTimeMillis(),
                        isFinal = false,
                    ),
                )
                emit(
                    NodeOutput.State(
                        AgentOrchestratorState.ObservationResult(resolvedToolName, "Execution denied by user"),
                    ),
                )
                emit(
                    NodeOutput.Result(
                        NodeExecutionResult(
                            outputText = "Execution denied by user",
                            resolvedToolName = resolvedToolName,
                        ),
                    ),
                )
                return@flow
            }
        }

        emit(NodeOutput.State(AgentOrchestratorState.ExecutingTool(resolvedToolName, resolvedToolArgs)))
        val result = try {
            toolRepository.executeTool(resolvedToolName, resolvedToolArgs)
        } catch (e: CancellationException) {
            // Preserve structured-concurrency cancellation: tool execution may suspend,
            // and a broad `catch (Exception)` would silently swallow the cancel.
            throw e
        } catch (e: Exception) {
            Timber.tag(
                "PipelineDebug",
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error executing tool: $resolvedToolName")
            "Error executing $resolvedToolName: ${e.message}"
        }

        emit(NodeOutput.State(AgentOrchestratorState.ObservationResult(resolvedToolName, result)))
        chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = Role.SYSTEM,
                content = "Observation from $resolvedToolName: $result",
                timestamp = System.currentTimeMillis(),
                isFinal = false,
            ),
        )
        emit(NodeOutput.Result(NodeExecutionResult(outputText = result, resolvedToolName = resolvedToolName)))
    }

    @androidx.annotation.VisibleForTesting
    internal fun parseToolSelection(response: String): Pair<String, String>? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        var jsonBlock = blockRegex.find(response)?.groups?.get(1)?.value

        if (jsonBlock == null) {
            val startIndex = response.indexOf('{')
            val endIndex = response.lastIndexOf('}')
            jsonBlock = if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                response.substring(startIndex, endIndex + 1)
            } else {
                response
            }
        }

        return try {
            val jsonObject = org.json.JSONObject(jsonBlock)
            val tool = if (jsonObject.has("tool")) jsonObject.getString("tool") else null
            val args = if (jsonObject.has("arguments")) {
                val argsObj = jsonObject.get("arguments")
                if (argsObj is org.json.JSONObject || argsObj is org.json.JSONArray) {
                    argsObj.toString()
                } else {
                    argsObj.toString()
                }
            } else {
                null
            }

            if (tool != null && args != null) {
                Pair(tool, args)
            } else {
                null
            }
        } catch (e: org.json.JSONException) {
            Timber.tag("PipelineDebug").e(e, "Error parsing tool selection JSON")
            null
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun parseToolArguments(response: String): String? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        var jsonBlock = blockRegex.find(response)?.groups?.get(1)?.value

        if (jsonBlock == null) {
            val startIndex = response.indexOf('{')
            val endIndex = response.lastIndexOf('}')
            jsonBlock = if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                response.substring(startIndex, endIndex + 1)
            } else {
                response
            }
        }

        return try {
            val jsonObject = org.json.JSONObject(jsonBlock)
            if (!jsonObject.has("arguments")) return null

            jsonObject.get("arguments").toString()
        } catch (e: org.json.JSONException) {
            Timber.tag("PipelineDebug").e(e, "Error parsing tool arguments JSON")
            null
        }
    }
}
