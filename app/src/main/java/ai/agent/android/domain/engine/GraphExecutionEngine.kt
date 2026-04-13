package ai.agent.android.domain.engine

import ai.agent.android.domain.engine.executors.NodeExecutorFactory
import ai.agent.android.domain.engine.executors.ToolNodeExecutor
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.*
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
    private val chatRepository: ChatRepository
) {
    companion object {
        const val MAX_STEPS = 15
    }

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

        var currentNode: NodeModel? = inputNode
        var stepCount = 0
        var currentInputText = userPrompt
        
        val activeQueue = mutableListOf<String>()
        var queueLoopStartNodeId: String? = null
        val queueResults = mutableListOf<String>()
        val traceSteps = mutableListOf<AgentOrchestratorState.TraceStep>()

        while (currentNode != null && stepCount < MAX_STEPS) {
            stepCount++
            
            // Emit the current pipeline stage
            emit(AgentOrchestratorState.PipelineStage(currentNode.type.name))
            
            // Give UI time to render the stage before CPU-heavy inference starts
            kotlinx.coroutines.delay(500)
            
            var nodeResult: NodeExecutionResult? = null
            
            val executor = nodeExecutorFactory.getExecutor(currentNode.type)
            executor.execute(currentNode, currentInputText, sessionId, userPrompt)
                .collect { stateOrResult ->
                    if (stateOrResult is AgentOrchestratorState) {
                        emit(stateOrResult)
                    } else if (stateOrResult is NodeExecutionResult) {
                        nodeResult = stateOrResult
                    }
                }
            
            if (nodeResult?.error != null) {
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
}