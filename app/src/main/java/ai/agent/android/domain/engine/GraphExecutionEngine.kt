package ai.agent.android.domain.engine

import ai.agent.android.domain.engine.executors.NodeExecutorFactory
import ai.agent.android.domain.engine.executors.ToolNodeExecutor
import ai.agent.android.domain.models.*
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
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
    private val metricsRepository: MetricsRepository,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
    private val nodeContextBuilder: NodeContextBuilder,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
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
        // For deterministic graphs (no routing/queue nodes) the total is fixed from the start.
        // For branching graphs it stays null until the active branch is resolved.
        val hasBranching = graph.nodes.any {
            it.type == NodeType.INTENT_ROUTER || it.type == NodeType.IF_CONDITION || it.type == NodeType.QUEUE_PROCESSOR
        }
        var estimatedTotalSteps: Int? = if (hasBranching) null else graph.nodes.size
        var currentNode: NodeModel? = inputNode
        var stepCount = 0
        var currentInputText = userPrompt

        val activeQueue = mutableListOf<String>()
        var activeQueueProcessorId: String? = null
        val queueResults = mutableListOf<String>()
        val traceSteps = mutableListOf<AgentOrchestratorState.TraceStep>()

        // Memory retrieval is keyed off the immutable userPrompt; resolve once and
        // reuse to avoid re-embedding the same query for every node iteration.
        val relevantMemories: List<MemoryChunk> = try {
            retrieveRelevantMemoryUseCase(userPrompt)
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").w(e, "Failed to retrieve long-term memories; continuing without them")
            emptyList()
        }
        // Tool invocations are accumulated as TOOL nodes complete and surfaced
        // via the `--- Tool Results ---` block on later nodes that opt in.
        val toolInvocationResults = mutableListOf<ToolInvocationResult>()

        while (currentNode != null && stepCount < maxSteps) {
            stepCount++
            
            // Emit current step with dynamically estimated total (null = still unknown).
            emit(AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(
                    stepIndex = stepCount,
                    totalSteps = estimatedTotalSteps,
                    nodeName = currentNode.type.name,
                )
            ))
            
            // Give UI time to render the stage before CPU-heavy inference starts
            kotlinx.coroutines.delay(500)
            
            var nodeResult: NodeExecutionResult? = null

            val executor = nodeExecutorFactory.getExecutor(currentNode.type)
            Timber.tag("PipelineDebug").d("[NODE_IN] type=${currentNode.type.name} id=${currentNode.id} input=${currentInputText.take(1000)}")

            // Render `$VARIABLE` placeholders in the node's system prompt before the LLM
            // sees it. We only touch nodes whose system prompt is actually fed into an LLM
            // engine — the others (TOOL, IF_CONDITION, INPUT, QUEUE_PROCESSOR) either ignore
            // `systemPrompt` or use it for non-LLM logic where placeholders are not expected.
            val nodeForExecution = renderNodeSystemPrompt(currentNode)

            // Compose the executor input by selecting only the context blocks the node
            // opted into via its [NodeContextConfig]. Control-flow nodes (INPUT,
            // IF_CONDITION, QUEUE_PROCESSOR) keep their raw passthrough semantics —
            // wrapping them would corrupt routing/queue state. OUTPUT in echo mode
            // (no systemPrompt) is also passed through so it forwards the upstream
            // result verbatim instead of leaking context headers to the user.
            val executorInput = if (shouldComposeContext(currentNode)) {
                val executionContext = PipelineExecutionContext(
                    originalUserMessage = userPrompt,
                    chatHistory = chatRepository.getMessagesForSession(sessionId).first(),
                    previousNodeOutput = currentInputText,
                    toolResults = toolInvocationResults.toList(),
                    memoryEntries = relevantMemories,
                )
                // No fallback to currentInputText: an empty result is the
                // intended outcome of a sparse config (e.g. only toolResults=true
                // before any tool has run). Step 3/6 forbids the all-flags-false
                // case at the validation layer, so we will not silently leak
                // previous-node output back into a node that opted out.
                nodeContextBuilder.build(currentNode.contextConfig, executionContext)
            } else {
                currentInputText
            }

            val nodeStartMs = System.currentTimeMillis()
            try {
                executor.execute(nodeForExecution, executorInput, sessionId, userPrompt)
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
            val nodeDurationMs = System.currentTimeMillis() - nodeStartMs
            val nodeTokenCount = nodeResult?.tokenCount
            metricsRepository.recordNodeExecution(currentNode.type.name, nodeDurationMs, nodeTokenCount)

            if (nodeResult?.error != null) {
                Timber.tag("PipelineDebug").e("[NODE_ERR] type=${currentNode.type.name} id=${currentNode.id} error=${nodeResult?.error}")
                emit(AgentOrchestratorState.Error(nodeResult?.error!!))
                return@flow
            }

            if (currentNode.type == NodeType.TOOL) {
                val toolOutput = nodeResult?.outputText ?: ""
                // Prefer the executor-resolved tool name so "auto"-configured TOOL
                // nodes attribute the observation to the tool that actually ran,
                // not the literal "auto" placeholder. Fall back to the node's
                // configured toolName, then the node label as a last resort.
                val toolName = nodeResult?.resolvedToolName
                    ?: currentNode.toolName?.takeUnless { it.equals("auto", ignoreCase = true) }
                    ?: currentNode.label
                toolInvocationResults += ToolInvocationResult(toolName = toolName, output = toolOutput)
            }

            if (currentNode.type != NodeType.INPUT && currentNode.type != NodeType.OUTPUT) {
                val outputText = nodeResult?.outputText ?: currentInputText
                traceSteps.add(
                    AgentOrchestratorState.TraceStep(
                        nodeName = currentNode.type.name,
                        outputText = outputText,
                        durationMs = nodeDurationMs,
                        tokenCount = nodeTokenCount,
                    )
                )
                chatRepository.saveTraceStep(
                    sessionId = sessionId,
                    nodeName = currentNode.type.name,
                    outputText = outputText,
                    durationMs = nodeDurationMs,
                    tokenCount = nodeTokenCount,
                )
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
                val doneNodeId = edges.find { it.label.equals("Done", ignoreCase = true) }?.targetNodeId

                if (activeQueue.isNotEmpty() && itemNodeId != null) {
                    // Compute dynamic total: current steps already done + all queue iterations + tail after queue.
                    val itemNode = graph.nodes.find { it.id == itemNodeId }
                    val doneNode = graph.nodes.find { it.id == doneNodeId }
                    val nodesPerItem = countNodesOnPath(itemNode, graph, stopNodeIds = setOf(currentNode.id))
                    val nodesAfterQueue = countNodesOnPath(doneNode, graph)
                    val totalItems = activeQueue.size // before removeAt — full queue size
                    estimatedTotalSteps = stepCount + totalItems * nodesPerItem + nodesAfterQueue

                    val nextItem = activeQueue.removeAt(0)
                    val contextStr = queueResults.mapIndexed { i, res -> "Result of Subtask ${i+1}:\n$res" }.joinToString("\n\n")
                    val subtaskInstruction = "CRITICAL INSTRUCTION: You are executing a single subtask within a larger workflow. Focus ONLY on this specific subtask. Do NOT provide conversational filler, and do NOT attempt to solve the overall task or future steps."
                    currentInputText = if (contextStr.isNotEmpty()) {
                        "PREVIOUS RESULTS CONTEXT:\n$contextStr\n\n---\n\n$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    } else {
                        "$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    }
                    currentNode = graph.nodes.find { it.id == itemNodeId }
                    continue
                } else {
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

            // After a branching node resolves its path, compute the estimated total for that branch.
            if (currentNode.type == NodeType.INTENT_ROUTER || currentNode.type == NodeType.IF_CONDITION) {
                estimatedTotalSteps = stepCount + countNodesOnPath(nextNode, graph)
            }
            
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

    /**
     * Counts the number of nodes reachable from [startNode] by following the first outgoing edge
     * of each node, including [startNode] itself. Stops at [NodeType.OUTPUT] (inclusive),
     * dead ends, already-visited nodes, or any node whose ID is in [stopNodeIds].
     *
     * Used to estimate the remaining steps on the active branch after a routing decision
     * or to measure the item-subgraph depth inside a [NodeType.QUEUE_PROCESSOR].
     *
     * @param startNode The node to start counting from, or null (returns 0).
     * @param graph The pipeline graph to traverse.
     * @param stopNodeIds IDs of nodes that act as exclusive stop boundaries (not counted).
     * @return The number of nodes on the path.
     */
    private fun countNodesOnPath(
        startNode: NodeModel?,
        graph: PipelineGraph,
        stopNodeIds: Set<String> = emptySet(),
    ): Int {
        var count = 0
        var node = startNode
        val visited = mutableSetOf<String>()
        while (node != null && node.id !in visited && node.id !in stopNodeIds) {
            visited.add(node.id)
            count++
            if (node.type == NodeType.OUTPUT) break
            val nextId = graph.connections.firstOrNull { it.sourceNodeId == node.id }?.targetNodeId
            node = graph.nodes.find { it.id == nextId }
        }
        return count
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

    /**
     * Decides whether [node]'s input string should be assembled by
     * [NodeContextBuilder] (true) or passed through as the raw
     * `currentInputText` (false).
     *
     * Composition is meaningful only for nodes that interpret their input as
     * an LLM prompt or a task description for a tool call. Control-flow nodes
     * ([NodeType.INPUT], [NodeType.IF_CONDITION], [NodeType.QUEUE_PROCESSOR])
     * either echo their input or use it for routing — wrapping them with
     * context headers would break their downstream contract.
     *
     * [NodeType.OUTPUT] in "echo" mode (no `systemPrompt`) is treated like a
     * passthrough so the user-visible response stays clean of context
     * scaffolding; an OUTPUT node with a configured `systemPrompt` is an LLM
     * formatter and benefits from the assembled context.
     */
    private fun shouldComposeContext(node: NodeModel): Boolean {
        if (node.type !in CONTEXT_AWARE_NODE_TYPES) return false
        if (node.type == NodeType.OUTPUT && node.systemPrompt.isNullOrBlank()) return false
        return true
    }

    /**
     * Returns a copy of [node] with its `systemPrompt` rendered through
     * [PromptTemplateEngine], substituting all `$VARIABLE` placeholders using the
     * injected [PromptVariableProvider] set. For nodes whose `systemPrompt` is
     * not consumed by an LLM (e.g. [NodeType.TOOL], [NodeType.IF_CONDITION]) the
     * original [node] instance is returned unchanged to avoid wasted work and
     * accidental substitution inside fields that happen to share the syntax.
     */
    private suspend fun renderNodeSystemPrompt(node: NodeModel): NodeModel {
        val rawPrompt = node.systemPrompt
        if (rawPrompt.isNullOrEmpty() || node.type !in LLM_NODE_TYPES) return node
        val rendered = promptTemplateEngine.render(rawPrompt, promptVariableProviders.toList())
        if (rendered === rawPrompt || rendered == rawPrompt) return node
        return node.copy(systemPrompt = rendered)
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

    private companion object {
        /**
         * Node types whose `systemPrompt` is forwarded to an LLM engine and
         * therefore needs `$VARIABLE` placeholders resolved before execution.
         * Includes [NodeType.LITE_RT], [NodeType.CLOUD], [NodeType.OUTPUT] from
         * the explicit task spec plus the other LLM-driven node types in this
         * codebase (`SUMMARY`, `INTENT_ROUTER`, `DECOMPOSITION`, `EVALUATION`).
         */
        private val LLM_NODE_TYPES: Set<NodeType> = setOf(
            NodeType.LITE_RT,
            NodeType.CLOUD,
            NodeType.OUTPUT,
            NodeType.SUMMARY,
            NodeType.INTENT_ROUTER,
            NodeType.DECOMPOSITION,
            NodeType.EVALUATION,
            NodeType.CLARIFICATION,
        )

        /**
         * Node types whose executor input is assembled via [NodeContextBuilder].
         * Mirrors [LLM_NODE_TYPES] plus [NodeType.TOOL] (which uses an internal
         * LLM call to choose tools / generate arguments and therefore benefits
         * from context blocks even when its `systemPrompt` is null).
         */
        private val CONTEXT_AWARE_NODE_TYPES: Set<NodeType> = setOf(
            NodeType.LITE_RT,
            NodeType.CLOUD,
            NodeType.OUTPUT,
            NodeType.SUMMARY,
            NodeType.INTENT_ROUTER,
            NodeType.DECOMPOSITION,
            NodeType.EVALUATION,
            NodeType.CLARIFICATION,
            NodeType.TOOL,
        )
    }
}