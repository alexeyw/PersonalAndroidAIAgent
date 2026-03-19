package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.models.RoutingDecision
import ai.agent.android.data.engine.KoogClientFactory
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.first

import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Use case that orchestrates the ReAct (Reasoning and Acting) cycle of the AI Agent.
 * It manages the loop of generating thoughts, deciding on actions, executing tools,
 * and feeding observations back to the LLM.
 */
@Singleton
class AgentOrchestratorUseCase @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val settingsRepository: SettingsRepository,
    private val metricsRepository: MetricsRepository,
    private val approvalNotifier: ai.agent.android.domain.services.ApprovalNotifier,
    private val taskRouterUseCase: TaskRouterUseCase,
    private val koogClientFactory: KoogClientFactory
) {
    companion object {
        const val MAX_ITERATIONS = 5
    }

    private val _globalState = MutableStateFlow<AgentOrchestratorState>(AgentOrchestratorState.Idle)
    val globalState: StateFlow<AgentOrchestratorState> = _globalState.asStateFlow()

    private val activeApprovalDeferreds = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()

    /**
     * Resumes the suspended ReAct cycle after the user has made a decision.
     *
     * @param sessionId The session ID that was waiting for approval.
     * @param isApproved True if the user allowed the action, false otherwise.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        val deferred = activeApprovalDeferreds.remove(sessionId)
        deferred?.complete(isApproved)
    }

    /**
     * Starts the orchestration cycle for a given user prompt.
     *
     * @param sessionId The current chat session ID.
     * @param userPrompt The new prompt from the user.
     * @return A [Flow] of [AgentOrchestratorState] emitting the progress of the agent.
     */
    operator fun invoke(sessionId: String, userPrompt: String): Flow<AgentOrchestratorState> = flow<AgentOrchestratorState> {
        emit(AgentOrchestratorState.Loading)

        // 1. Save the user's message
        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = Role.USER,
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load available tools and construct the system prompt
        val tools = toolRepository.getAvailableTools()
        val toolsDescription = tools.joinToString("\n") { 
            "- ${it.name}: ${it.description} | Params: ${it.parameters}" 
        }

        val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
        val toolUsageInstructionTemplate = settingsRepository.toolUsageInstruction.first()
        val toolUsageInstruction = String.format(toolUsageInstructionTemplate, toolsDescription)

        val baseSystemPrompt = """
            $systemPromptPrefix
            $toolUsageInstruction
        """.trimIndent()

        // ReAct loop
        var currentIteration = 0
        var isCompleted = false

        while (currentIteration < MAX_ITERATIONS && !isCompleted) {
            currentIteration++

            // 3. Get the context window (history + previous thoughts/observations)
            val contextWindow = getContextWindowUseCase(sessionId)
            val fullPrompt = "$baseSystemPrompt\n\n$contextWindow\nAGENT: "

            // 4. Request generation from the routed LLM
            val startTime = System.currentTimeMillis()
            val decision = taskRouterUseCase(userPrompt)
            
            val responseStream = when (decision) {
                is RoutingDecision.LocalLiteRT -> {
                    llmEngine.generateResponseStream(fullPrompt)
                }
                is RoutingDecision.LocalOllama -> {
                    val client = koogClientFactory.createOllamaExecutor()
                    if (client != null) {
                        val model = client.models().firstOrNull() ?: ai.koog.prompt.llm.LLModel(client.llmProvider(), "llama3")
                        client.executeStreaming(prompt("default") { user(fullPrompt) }, model).mapNotNull { frame ->
                            (frame as? StreamFrame.TextDelta)?.text
                        }
                    } else {
                        flow { emit("Error: Ollama not configured.") }
                    }
                }
                is RoutingDecision.CloudLLM -> {
                    val client = when (decision.provider) {
                        "openai" -> koogClientFactory.createOpenAIExecutor()
                        "anthropic" -> koogClientFactory.createAnthropicExecutor()
                        "deepseek" -> koogClientFactory.createDeepSeekExecutor()
                        else -> null
                    }
                    if (client != null) {
                        val model = client.models().firstOrNull() ?: ai.koog.prompt.llm.LLModel(client.llmProvider(), "default")
                        client.executeStreaming(prompt("default") { user(fullPrompt) }, model).mapNotNull { frame ->
                            (frame as? StreamFrame.TextDelta)?.text
                        }
                    } else {
                        flow { emit("Error: Cloud LLM provider not configured.") }
                    }
                }
            }
            
            val accumulatedResponse = StringBuilder()
            var emittedThinking = false
            var approximateTokenCount = 0

            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                    approximateTokenCount += token.length / 4 + 1
                    
                    if (!emittedThinking) {
                        emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                        emittedThinking = true
                    } else {
                        emit(AgentOrchestratorState.Answering(accumulatedResponse.toString()))
                    }
                }
            } catch (e: Exception) {
                emit(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation"))
                return@flow
            }
            
            val endTime = System.currentTimeMillis()
            metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

            val fullResponseText = accumulatedResponse.toString().trim()
            
            // 5. Save the agent's raw response to history
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.AGENT,
                    content = fullResponseText,
                    timestamp = System.currentTimeMillis()
                )
            )

            // 6. Parse the response to check if a tool needs to be called
            val toolCall = parseToolCall(fullResponseText)
            
            if (toolCall != null) {
                val (toolName, toolArgs) = toolCall
                val requiresUserConfirmation = settingsRepository.requiresUserConfirmation.first()

                if (requiresUserConfirmation) {
                    emit(AgentOrchestratorState.WaitingForApproval(toolName, toolArgs))
                    approvalNotifier.sendApprovalRequest(sessionId, toolName, toolArgs)
                    
                    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    activeApprovalDeferreds[sessionId] = deferred
                    val isApproved = deferred.await()
                    
                    if (!isApproved) {
                        chatRepository.saveMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = Role.SYSTEM,
                                content = "User denied execution of tool: $toolName",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        emit(AgentOrchestratorState.ObservationResult(toolName, "Execution denied by user"))
                        continue
                    }
                }
                
                emit(AgentOrchestratorState.ExecutingTool(toolName, toolArgs))

                // Execute the tool
                val result = try {
                    toolRepository.executeTool(toolName, toolArgs)
                } catch (e: Exception) {
                    "Error executing $toolName: ${e.message}"
                }

                emit(AgentOrchestratorState.ObservationResult(toolName, result))

                // Save the observation to the history as a SYSTEM message so the LLM sees it
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "Observation from $toolName: $result",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                // No tool called, the agent answered directly
                isCompleted = true
                emit(AgentOrchestratorState.Completed(fullResponseText))
            }
        }

        if (!isCompleted) {
            emit(AgentOrchestratorState.Error("Reached maximum iterations ($MAX_ITERATIONS) without a final answer."))
        }
    }.onEach { state ->
        _globalState.value = state
    }.onCompletion {
        _globalState.value = AgentOrchestratorState.Idle
    }

    /**
     * Extremely simple parser to find a JSON block for tool calling.
     * Real implementation might need more robust regex or a JSON parser.
     */
    private fun parseToolCall(response: String): Pair<String, String>? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val blockMatch = blockRegex.find(response) ?: return null
        val jsonBlock = blockMatch.groups[1]?.value ?: return null
        
        val toolMatch = """"tool"\s*:\s*"([^"]+)"""".toRegex().find(jsonBlock)
        val toolName = toolMatch?.groups?.get(1)?.value ?: return null
        
        val argsIndex = jsonBlock.indexOf("\"arguments\"")
        if (argsIndex == -1) return null
        
        val colonIndex = jsonBlock.indexOf(":", argsIndex)
        if (colonIndex == -1) return null
        
        val valueStart = jsonBlock.substring(colonIndex + 1).trimStart()
        
        var arguments = ""
        if (valueStart.startsWith("\"")) {
            // It's a JSON string, find the end of it, ignoring escaped quotes
            var endQuoteIndex = -1
            for (i in 1 until valueStart.length) {
                if (valueStart[i] == '"' && valueStart[i - 1] != '\\') {
                    endQuoteIndex = i
                    break
                }
            }
            if (endQuoteIndex != -1) {
                arguments = valueStart.substring(1, endQuoteIndex)
                arguments = arguments.replace("\\\"", "\"").replace("\\\\", "\\")
            }
        } else if (valueStart.startsWith("{")) {
            // It's a JSON object
            val endBraceIndex = valueStart.lastIndexOf("}")
            if (endBraceIndex != -1) {
                arguments = valueStart.substring(0, endBraceIndex + 1)
            }
        } else {
            return null
        }
        
        return Pair(toolName, arguments)
    }
}
