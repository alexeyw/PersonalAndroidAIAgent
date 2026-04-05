package ai.agent.android.domain.engine

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.*
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine responsible for executing a given [PipelineGraph].
 * It traverses nodes starting from [NodeType.INPUT], evaluates conditions,
 * executes LLM inference, triggers tools, and reaches [NodeType.OUTPUT].
 */
@Singleton
class GraphExecutionEngine @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val metricsRepository: MetricsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val koogClientFactory: KoogClientFactory,
    private val evaluateIfConditionUseCase: EvaluateIfConditionUseCase,
    private val loadModelUseCase: LoadModelUseCase
) {
    companion object {
        const val MAX_STEPS = 15
    }

    private val activeApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Resumes execution after user approval.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        val deferred = activeApprovalDeferreds.remove(sessionId)
        deferred?.complete(isApproved)
    }

    /**
     * Executes the graph by processing nodes sequentially.
     */
    operator fun invoke(
        sessionId: String,
        userPrompt: String,
        graph: PipelineGraph
    ): Flow<AgentOrchestratorState> = flow {
        if (!graph.isValidDAG()) {
            emit(AgentOrchestratorState.Error("Pipeline graph contains cycles and is invalid."))
            return@flow
        }

        val inputNode = graph.nodes.find { it.type == NodeType.INPUT }
        if (inputNode == null) {
            emit(AgentOrchestratorState.Error("Pipeline has no INPUT node"))
            return@flow
        }

        var currentNode: NodeModel? = inputNode
        var stepCount = 0
        var currentInputText = userPrompt
        
        val activeQueue = mutableListOf<String>()
        var queueLoopStartNodeId: String? = null
        val queueResults = mutableListOf<String>()

        while (currentNode != null && stepCount < MAX_STEPS) {
            stepCount++
            
            // Emit the current pipeline stage
            emit(AgentOrchestratorState.PipelineStage(currentNode.type.name))
            
            // Give UI time to render the stage before CPU-heavy inference starts
            kotlinx.coroutines.delay(500)
            
            // Execute the current node and collect its states to emit them
            var nodeResult: NodeExecutionResult? = null
            executeNode(currentNode, currentInputText, sessionId, userPrompt)
                .collect { stateOrResult ->
                    if (stateOrResult is AgentOrchestratorState) {
                        emit(stateOrResult)
                    } else if (stateOrResult is NodeExecutionResult) {
                        nodeResult = stateOrResult
                    }
                }
            
            if (nodeResult?.error != null) {
                emit(AgentOrchestratorState.Error(nodeResult!!.error!!))
                return@flow
            }

            if (currentNode.type == NodeType.OUTPUT) {
                return@flow
            }

            if (currentNode.type == NodeType.QUEUE_PROCESSOR) {
                val list = parseListFromText(nodeResult?.outputText ?: currentInputText)
                activeQueue.clear()
                activeQueue.addAll(list)
                queueResults.clear()
                queueLoopStartNodeId = findNextNodeId(currentNode, graph, nodeResult?.conditionResult, nodeResult?.routingKey)
                
                if (activeQueue.isNotEmpty()) {
                    currentInputText = activeQueue.removeAt(0)
                    currentNode = graph.nodes.find { it.id == queueLoopStartNodeId }
                    continue
                }
            }

            currentInputText = nodeResult?.outputText ?: currentInputText
            
            val nextNodeId = findNextNodeId(currentNode, graph, nodeResult?.conditionResult, nodeResult?.routingKey)
            val nextNode = graph.nodes.find { it.id == nextNodeId }
            
            if (queueLoopStartNodeId != null && (nextNode == null || nextNode.type == NodeType.SUMMARY)) {
                queueResults.add(currentInputText)
                
                if (activeQueue.isNotEmpty()) {
                    currentInputText = activeQueue.removeAt(0)
                    currentNode = graph.nodes.find { it.id == queueLoopStartNodeId }
                    continue
                } else {
                    currentInputText = "Queue execution completed.\nResults:\n" + queueResults.mapIndexed { i, res -> "${i+1}. $res" }.joinToString("\n")
                    queueLoopStartNodeId = null
                    currentNode = nextNode
                    continue
                }
            }

            currentNode = nextNode
        }

        if (stepCount >= MAX_STEPS) {
            emit(AgentOrchestratorState.Error("Pipeline execution exceeded maximum steps ($MAX_STEPS)"))
        } else {
            // Loop exited because currentNode became null before reaching OUTPUT
            emit(AgentOrchestratorState.Error("Pipeline execution terminated unexpectedly without reaching OUTPUT node."))
        }
    }

    private data class NodeExecutionResult(
        val outputText: String? = null,
        val error: String? = null,
        val conditionResult: Boolean? = null,
        val routingKey: String? = null
    )

    private fun executeNode(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        when (node.type) {
            NodeType.INPUT -> {
                emit(NodeExecutionResult(outputText = inputText))
            }
            NodeType.OUTPUT -> {
                emit(AgentOrchestratorState.Completed(inputText))
                emit(NodeExecutionResult(outputText = inputText))
            }
            NodeType.IF_CONDITION -> {
                val isTrue = evaluateIfConditionUseCase(node, inputText)
                emit(NodeExecutionResult(conditionResult = isTrue, outputText = inputText))
            }
            NodeType.TOOL -> {
                val toolName = node.toolName
                if (toolName.isNullOrBlank()) {
                    emit(NodeExecutionResult(error = "Tool node is missing toolName configuration."))
                    return@flow
                }

                val toolArgs = parseToolArguments(inputText) ?: inputText
                val requiresUserConfirmation = settingsRepository.requiresUserConfirmation.first()
                var isApproved = true

                if (requiresUserConfirmation) {
                    emit(AgentOrchestratorState.WaitingForApproval(toolName, toolArgs))
                    approvalNotifier.sendApprovalRequest(sessionId, toolName, toolArgs)
                    
                    val deferred = CompletableDeferred<Boolean>()
                    activeApprovalDeferreds[sessionId] = deferred
                    isApproved = deferred.await()
                    
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
                        emit(NodeExecutionResult(outputText = "Execution denied by user"))
                        return@flow
                    }
                }
                
                emit(AgentOrchestratorState.ExecutingTool(toolName, toolArgs))
                val result = try {
                    toolRepository.executeTool(toolName, toolArgs)
                } catch (e: Exception) {
                    "Error executing $toolName: ${e.message}"
                }
                
                emit(AgentOrchestratorState.ObservationResult(toolName, result))
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "Observation from $toolName: $result",
                        timestamp = System.currentTimeMillis()
                    )
                )
                emit(NodeExecutionResult(outputText = result))
            }
            NodeType.LITE_RT, NodeType.OPENAI, NodeType.ANTHROPIC, NodeType.GOOGLE, NodeType.DEEPSEEK -> {
                val tools = toolRepository.getAvailableTools()
                val toolsDescription = tools.joinToString("\n") { "- ${it.name}: ${it.description} | Params: ${it.parameters}" }
                val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
                val toolUsageInstructionTemplate = settingsRepository.toolUsageInstruction.first()
                val toolUsageInstruction = String.format(toolUsageInstructionTemplate, toolsDescription)

                val baseSystemPrompt = "$systemPromptPrefix\n$toolUsageInstruction\n"
                val contextWindow = getContextWindowUseCase(sessionId)
                
                val relevantMemories = retrieveRelevantMemoryUseCase(originalPrompt)
                val memoryContext = if (relevantMemories.isNotEmpty()) {
                    "RELEVANT LONG-TERM MEMORIES:\n" + relevantMemories.joinToString("\n") { "- ${it.text}" } + "\n\n"
                } else {
                    ""
                }

                val fullPrompt = "$baseSystemPrompt\n\n$memoryContext$contextWindow\nAGENT: "
                
                val startTime = System.currentTimeMillis()
                
                val responseStream = when (node.type) {
                    NodeType.LITE_RT -> {
                        val loadResult = loadModelUseCase(node.modelPath)
                        if (loadResult is Result.Error) {
                            flowOf("Error loading local model: ${loadResult.message}")
                        } else {
                            llmEngine.generateResponseStream(fullPrompt)
                        }
                    }
                    NodeType.OPENAI -> {
                        val client = koogClientFactory.createOpenAIExecutor()
                        val modelName = apiKeyRepository.getOpenAIModel().first() ?: OpenAIModels.Chat.GPT5_4.id
                        client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getOpenAIModel(modelName))
                            ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: OpenAI not configured")
                    }
                    NodeType.ANTHROPIC -> {
                        val client = koogClientFactory.createAnthropicExecutor()
                        val modelName = apiKeyRepository.getAnthropicModel().first() ?: AnthropicModels.Sonnet_4_5.id
                        client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getAnthropicModel(modelName))
                            ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: Anthropic not configured")
                    }
                    NodeType.GOOGLE -> {
                        val client = koogClientFactory.createGoogleExecutor()
                        val modelName = apiKeyRepository.getGoogleModel().first() ?: GoogleModels.Gemini3_Flash_Preview.id
                        client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getGoogleModel(modelName))
                            ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: Google not configured")
                    }
                    NodeType.DEEPSEEK -> {
                        val client = koogClientFactory.createDeepSeekExecutor()
                        val modelName = apiKeyRepository.getDeepSeekModel().first() ?: DeepSeekModels.DeepSeekChat.id
                        client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getDeepSeekModel(modelName))
                            ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: DeepSeek not configured")
                    }
                    else -> flowOf("Error: Unknown LLM provider")
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
                    emit(NodeExecutionResult(error = e.message))
                    return@flow
                }
                
                val endTime = System.currentTimeMillis()
                metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

                val fullResponseText = accumulatedResponse.toString().trim()
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.AGENT,
                        content = fullResponseText,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                kotlinx.coroutines.delay(1000)

                emit(NodeExecutionResult(outputText = fullResponseText))
            }
            NodeType.INTENT_ROUTER, NodeType.DECOMPOSITION, NodeType.EVALUATION, NodeType.SUMMARY -> {
                val nodeSystemPrompt = node.systemPrompt ?: "You are an AI assistant."
                val fullPrompt = "$nodeSystemPrompt\n\nUSER: $inputText\nAGENT: "
                
                val loadResult = loadModelUseCase(node.modelPath)
                if (loadResult is Result.Error) {
                    val errorMsg = "Error loading local model for system node: ${loadResult.message}"
                    emit(AgentOrchestratorState.Error(errorMsg))
                    emit(NodeExecutionResult(error = errorMsg))
                    return@flow
                }
                val responseStream = llmEngine.generateResponseStream(fullPrompt)
                val accumulatedResponse = StringBuilder()
                
                try {
                    responseStream.collect { token ->
                        accumulatedResponse.append(token)
                        emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                    }
                } catch (e: Exception) {
                    emit(AgentOrchestratorState.Error(e.message ?: "Unknown error"))
                    emit(NodeExecutionResult(error = e.message))
                    return@flow
                }
                
                // If model output nothing, provide a fallback to avoid UI appearing "stuck" or "doing nothing"
                val generatedText = accumulatedResponse.toString().trim()
                val fullResponseText = if (generatedText.isNotEmpty()) generatedText else "No response generated by model."
                val routingKey = if (node.type == NodeType.INTENT_ROUTER) fullResponseText else null
                
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.AGENT,
                        content = fullResponseText,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                kotlinx.coroutines.delay(1000)
                
                emit(NodeExecutionResult(outputText = fullResponseText, routingKey = routingKey))
            }
            NodeType.QUEUE_PROCESSOR -> {
                emit(NodeExecutionResult(outputText = inputText))
            }
        }
    }

    private fun findNextNodeId(
        currentNode: NodeModel, 
        graph: PipelineGraph, 
        conditionResult: Boolean?,
        routingKey: String? = null
    ): String? {
        val edges = graph.connections.filter { it.sourceNodeId == currentNode.id }
        if (edges.isEmpty()) return null

        if (currentNode.type == NodeType.IF_CONDITION) {
            val expectedLabel = if (conditionResult == true) "True" else "False"
            return edges.find { it.label.equals(expectedLabel, ignoreCase = true) }?.targetNodeId 
                ?: edges.firstOrNull()?.targetNodeId
        }

        if (currentNode.type == NodeType.INTENT_ROUTER && routingKey != null) {
            val matchedEdge = edges.find { it.label?.equals(routingKey, ignoreCase = true) == true }
                ?: edges.find { !it.label.isNullOrBlank() && routingKey.contains(it.label, ignoreCase = true) }
            if (matchedEdge != null) return matchedEdge.targetNodeId
        }

        return edges.firstOrNull()?.targetNodeId
    }

    private fun parseListFromText(text: String): List<String> {
        try {
            val blockRegex = """```json\s*(\[.*?\])\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val blockMatch = blockRegex.find(text)
            val jsonString = blockMatch?.groups?.get(1)?.value ?: text.trim()
            
            if (jsonString.startsWith("[")) {
                val jsonArray = org.json.JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                if (list.isNotEmpty()) return list
            }
        } catch (e: Exception) {
            // Ignore JSON parse errors and fallback
        }
        
        val lines = text.lines().map { it.trim() }.filter { it.matches(Regex("""^(\d+\.|-|\*)\s+.*""")) }
        if (lines.isNotEmpty()) {
            return lines.map { it.replaceFirst(Regex("""^(\d+\.|-|\*)\s+"""), "") }
        }
        
        return listOf(text)
    }

    private fun parseToolArguments(response: String): String? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val blockMatch = blockRegex.find(response) ?: return null
        val jsonBlock = blockMatch.groups[1]?.value ?: return null
        
        val argsIndex = jsonBlock.indexOf("\"arguments\"")
        if (argsIndex == -1) return null
        
        val colonIndex = jsonBlock.indexOf(":", argsIndex)
        if (colonIndex == -1) return null
        
        val valueStart = jsonBlock.substring(colonIndex + 1).trimStart()
        
        var arguments = ""
        if (valueStart.startsWith("\"")) {
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
            val endBraceIndex = valueStart.lastIndexOf("}")
            if (endBraceIndex != -1) {
                arguments = valueStart.substring(0, endBraceIndex + 1)
            }
        } else {
            return null
        }
        
        return arguments
    }
}
