package ai.agent.android.domain.engine

import ai.agent.android.domain.engine.executors.NodeExecutorFactory
import ai.agent.android.domain.engine.executors.ToolNodeExecutor
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine responsible for executing a given [PipelineGraph].
 * It traverses nodes starting from [NodeType.INPUT], evaluates conditions,
 * executes LLM inference, triggers tools, and reaches [NodeType.OUTPUT].
 */
@Singleton
class GraphExecutionEngine @Inject constructor(
    private val nodeExecutorFactory: NodeExecutorFactory,
    private val toolNodeExecutor: ToolNodeExecutor,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Resumes execution after user approval.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        toolNodeExecutor.resumeWithApproval(sessionId, isApproved)
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

        val maxSteps = settingsRepository.pipelineMaxSteps.first()
        val totalSteps = graph.nodes.size
        var currentNode: NodeModel? = inputNode
        var stepCount = 0
        var currentInputText = userPrompt
        
        val activeQueue = mutableListOf<String>()
        var activeQueueProcessorId: String? = null
        val queueResults = mutableListOf<String>()
        val traceSteps = mutableListOf<AgentOrchestratorState.TraceStep>()

        while (currentNode != null && stepCount < maxSteps) {
            stepCount++
            
            // Emit the current pipeline stage with progress info.
            // stepIndex is capped at totalSteps to avoid "Step 6 of 5" in looping graphs.
            emit(AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(
                    stepIndex = minOf(stepCount, totalSteps),
                    totalSteps = totalSteps,
                    nodeName = currentNode.type.name,
                )
            ))
            
            // Give UI time to render the stage before CPU-heavy inference starts
            kotlinx.coroutines.delay(500)
            
            var nodeResult: NodeExecutionResult? = null
            
            val executor = nodeExecutorFactory.getExecutor(currentNode.type)
            Timber.tag("PipelineDebug").d("[NODE_IN] type=${currentNode.type.name} id=${currentNode.id} input=${currentInputText.take(1000)}")
            
            try {
                executor.execute(currentNode, currentInputText, sessionId, userPrompt)
                    .collect { stateOrResult ->
                        if (stateOrResult is AgentOrchestratorState) {
                            emit(stateOrResult)
                        } else if (stateOrResult is NodeExecutionResult) {
                            nodeResult = stateOrResult
                        }
                    }
                
                Timber.tag("PipelineDebug").d("[NODE_OUT] type=${currentNode.type.name} id=${currentNode.id} output=${nodeResult?.outputText?.take(1000)}")
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").e(e, "[NODE_ERR] type=${currentNode.type.name} id=${currentNode.id} error=${e.message}")
                emit(AgentOrchestratorState.Error(e.message ?: "Unknown error"))
                return@flow
            }
            
            if (nodeResult?.error != null) {
                Timber.tag("PipelineDebug").e("[NODE_ERR] type=${currentNode.type.name} id=${currentNode.id} error=${nodeResult?.error}")
                emit(AgentOrchestratorState.Error(nodeResult?.error!!))
                return@flow
            }

            if (currentNode.type != NodeType.INPUT && currentNode.type != NodeType.OUTPUT) {
                val outputText = nodeResult?.outputText ?: currentInputText
                traceSteps.add(AgentOrchestratorState.TraceStep(currentNode.type.name, outputText))
                chatRepository.saveTraceStep(sessionId, currentNode.type.name, outputText)
                emit(AgentOrchestratorState.PipelineTrace(traceSteps.toList()))
            }

            if (currentNode.type == NodeType.OUTPUT) {
                return@flow
            }

            if (currentNode.type == NodeType.QUEUE_PROCESSOR) {
                val list = parseListFromText(nodeResult?.outputText ?: currentInputText)
                activeQueue.clear()
                activeQueue.addAll(list)
                queueResults.clear()
                activeQueueProcessorId = currentNode.id
                
                val edges = graph.connections.filter { it.sourceNodeId == currentNode.id }
                val itemNodeId = edges.find { it.label.equals("Item", ignoreCase = true) }?.targetNodeId 
                    ?: edges.firstOrNull()?.targetNodeId
                
                if (activeQueue.isNotEmpty() && itemNodeId != null) {
                    val nextItem = activeQueue.removeAt(0)
                    val contextStr = queueResults.mapIndexed { i, res -> "Result of Subtask ${i+1}:\n$res" }.joinToString("\n\n")
                    val subtaskInstruction = "CRITICAL INSTRUCTION: You are executing a single subtask within a larger workflow. Focus ONLY on this specific subtask. Do NOT provide conversational filler, and do NOT attempt to solve the overall task or future steps."
                    if (contextStr.isNotEmpty()) {
                        currentInputText = "PREVIOUS RESULTS CONTEXT:\n$contextStr\n\n---\n\n$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    } else {
                        currentInputText = "$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    }
                    currentNode = graph.nodes.find { it.id == itemNodeId }
                    continue
                } else {
                    val doneNodeId = edges.find { it.label.equals("Done", ignoreCase = true) }?.targetNodeId
                    activeQueueProcessorId = null
                    currentNode = graph.nodes.find { it.id == doneNodeId }
                    continue
                }
            }

            // INTENT_ROUTER's outputText is the routing key — a control signal, not a content payload.
            // Preserve currentInputText so downstream nodes receive the original data, not the routing label.
            currentInputText = if (currentNode.type == NodeType.INTENT_ROUTER) {
                currentInputText
            } else {
                nodeResult?.outputText ?: currentInputText
            }
            
            val nextNodeId = findNextNodeId(currentNode, graph, nodeResult?.conditionResult, nodeResult?.routingKey)
            val nextNode = graph.nodes.find { it.id == nextNodeId }
            
            if (activeQueueProcessorId != null && (nextNode == null || nextNode.type == NodeType.QUEUE_PROCESSOR)) {
                queueResults.add(currentInputText)
                
                val edges = graph.connections.filter { it.sourceNodeId == activeQueueProcessorId }
                val itemNodeId = edges.find { it.label.equals("Item", ignoreCase = true) }?.targetNodeId 
                    ?: edges.firstOrNull()?.targetNodeId
                val doneNodeId = edges.find { it.label.equals("Done", ignoreCase = true) }?.targetNodeId
                
                if (activeQueue.isNotEmpty() && itemNodeId != null) {
                    val nextItem = activeQueue.removeAt(0)
                    val contextStr = queueResults.mapIndexed { i, res -> "Result of Subtask ${i+1}:\n$res" }.joinToString("\n\n")
                    val subtaskInstruction = "CRITICAL INSTRUCTION: You are executing a single subtask within a larger workflow. Focus ONLY on this specific subtask. Do NOT provide conversational filler, and do NOT attempt to solve the overall task or future steps."
                    if (contextStr.isNotEmpty()) {
                        currentInputText = "PREVIOUS RESULTS CONTEXT:\n$contextStr\n\n---\n\n$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    } else {
                        currentInputText = "$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    }
                    currentNode = graph.nodes.find { it.id == itemNodeId }
                    continue
                } else {
                    currentInputText = "Queue execution completed.\nResults:\n" + queueResults.mapIndexed { i, res -> "${i+1}. $res" }.joinToString("\n")
                    activeQueueProcessorId = null
                    currentNode = graph.nodes.find { it.id == doneNodeId }
                    continue
                }
            }

            currentNode = nextNode
        }

        if (stepCount >= maxSteps) {
            emit(AgentOrchestratorState.Error("Pipeline execution exceeded maximum steps ($maxSteps)"))
        } else {
            // Loop exited because currentNode became null before reaching OUTPUT
            emit(AgentOrchestratorState.Error("Pipeline execution terminated unexpectedly without reaching OUTPUT node."))
        }
    }

    private fun findNextNodeId(
        currentNode: NodeModel, 
        graph: PipelineGraph, 
        conditionResult: Boolean?,
        routingKey: String? = null
    ): String? {
        val edges = graph.connections.filter { it.sourceNodeId == currentNode.id }
        if (edges.isEmpty()) {
            Timber.tag("PipelineDebug").d("[ROUTE] from=${currentNode.id} label=null -> to=null")
            return null
        }

        val targetNodeId = if (currentNode.type == NodeType.IF_CONDITION) {
            val expectedLabel = if (conditionResult == true) "True" else "False"
            edges.find { it.label.equals(expectedLabel, ignoreCase = true) }?.targetNodeId 
                ?: edges.firstOrNull()?.targetNodeId
        } else if (currentNode.type == NodeType.INTENT_ROUTER && routingKey != null) {
            val matchedEdge = edges.find { it.label?.equals(routingKey, ignoreCase = true) == true }
                ?: edges.find { !it.label.isNullOrBlank() && routingKey.contains(it.label, ignoreCase = true) }
            matchedEdge?.targetNodeId ?: edges.firstOrNull()?.targetNodeId
        } else {
            edges.firstOrNull()?.targetNodeId
        }
        
        val edgeLabel = edges.find { it.targetNodeId == targetNodeId }?.label ?: "null"
        Timber.tag("PipelineDebug").d("[ROUTE] from=${currentNode.id} label=$edgeLabel -> to=$targetNodeId")
        return targetNodeId
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
            Timber.tag("PipelineDebug").e(e, "Error parsing JSON list")
        }
        
        val lines = text.lines().map { it.trim() }.filter { it.matches(Regex("""^(\d+\.|-|\*)\s+.*""")) }
        if (lines.isNotEmpty()) {
            return lines.map { it.replaceFirst(Regex("""^(\d+\.|-|\*)\s+"""), "") }
        }
        
        return listOf(text)
    }
}