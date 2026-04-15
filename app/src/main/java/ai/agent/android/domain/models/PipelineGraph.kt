package ai.agent.android.domain.models

/**
 * Represents a complete pipeline graph composed of nodes and directed connections.
 * Forms a Directed Acyclic Graph (DAG) for execution.
 *
 * @property id The unique identifier of the pipeline.
 * @property name The display name of the pipeline.
 * @property nodes The list of [NodeModel]s present in the pipeline.
 * @property connections The list of [ConnectionModel]s linking the nodes.
 * @property updatedAt Timestamp of the last update.
 */
data class PipelineGraph(
    val id: String,
    val name: String,
    val nodes: List<NodeModel> = emptyList(),
    val connections: List<ConnectionModel> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Validates if the current graph is a valid Directed Acyclic Graph (DAG).
     *
     * @return True if the graph contains no cycles, false otherwise.
     */
    fun isValidDAG(): Boolean {
        if (nodes.isEmpty()) return true

        val adjacencyList = mutableMapOf<String, MutableList<String>>()
        nodes.forEach { adjacencyList[it.id] = mutableListOf() }
        
        connections.forEach { connection ->
            val targetNode = nodes.find { it.id == connection.targetNodeId }
            // Ignore back-edges to QUEUE_PROCESSOR for DAG validation
            if (targetNode?.type != NodeType.QUEUE_PROCESSOR) {
                if (adjacencyList.containsKey(connection.sourceNodeId)) {
                    adjacencyList[connection.sourceNodeId]?.add(connection.targetNodeId)
                }
            }
        }

        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun isCyclic(nodeId: String): Boolean {
            if (recursionStack.contains(nodeId)) return true
            if (visited.contains(nodeId)) return false

            visited.add(nodeId)
            recursionStack.add(nodeId)

            val neighbors = adjacencyList[nodeId] ?: emptyList()
            for (neighbor in neighbors) {
                if (isCyclic(neighbor)) return true
            }

            recursionStack.remove(nodeId)
            return false
        }

        for (node in nodes) {
            if (!visited.contains(node.id)) {
                if (isCyclic(node.id)) {
                    return false // Cycle detected
                }
            }
        }

        return true
    }

    /**
     * Validates the pipeline graph and returns a list of validation errors.
     *
     * @return A list of [PipelineValidationError]s. Empty if valid.
     */
    fun validate(): List<PipelineValidationError> {
        val errors = mutableListOf<PipelineValidationError>()
        
        val inputs = nodes.filter { it.type == NodeType.INPUT }
        if (inputs.isEmpty()) errors.add(PipelineValidationError.MissingInput)
        if (inputs.size > 1) errors.add(PipelineValidationError.MultipleInputs)

        val outputs = nodes.filter { it.type == NodeType.OUTPUT }
        if (outputs.isEmpty()) errors.add(PipelineValidationError.MissingOutput)
        if (outputs.size > 1) errors.add(PipelineValidationError.MultipleOutputs)

        if (!isValidDAG()) {
            errors.add(PipelineValidationError.HasCycles)
        }

        if (nodes.isNotEmpty()) {
            val adjForward = mutableMapOf<String, MutableList<String>>()
            val adjBackward = mutableMapOf<String, MutableList<String>>()
            nodes.forEach { 
                adjForward[it.id] = mutableListOf()
                adjBackward[it.id] = mutableListOf()
            }
            connections.forEach { 
                if (adjForward.containsKey(it.sourceNodeId)) {
                    adjForward[it.sourceNodeId]?.add(it.targetNodeId)
                }
                if (adjBackward.containsKey(it.targetNodeId)) {
                    adjBackward[it.targetNodeId]?.add(it.sourceNodeId)
                }
            }

            val hasDisconnectedInput = inputs.any { adjForward[it.id]?.isEmpty() == true }
            if (hasDisconnectedInput) errors.add(PipelineValidationError.DisconnectedInput)

            val hasDisconnectedOutput = outputs.any { adjBackward[it.id]?.isEmpty() == true }
            if (hasDisconnectedOutput) errors.add(PipelineValidationError.DisconnectedOutput)

            val reachableFromInput = mutableSetOf<String>()
            val inputQueue = ArrayDeque<String>()
            inputs.forEach { 
                reachableFromInput.add(it.id)
                inputQueue.add(it.id)
            }
            while (inputQueue.isNotEmpty()) {
                val curr = inputQueue.removeFirst()
                adjForward[curr]?.forEach { next ->
                    if (reachableFromInput.add(next)) {
                        inputQueue.add(next)
                    }
                }
            }

            val canReachOutput = mutableSetOf<String>()
            val outputQueue = ArrayDeque<String>()
            outputs.forEach { 
                canReachOutput.add(it.id)
                outputQueue.add(it.id)
            }
            while (outputQueue.isNotEmpty()) {
                val curr = outputQueue.removeFirst()
                adjBackward[curr]?.forEach { prev ->
                    if (canReachOutput.add(prev)) {
                        outputQueue.add(prev)
                    }
                }
            }

            if (nodes.any { it.id !in reachableFromInput }) {
                errors.add(PipelineValidationError.UnreachableNode)
            }

            if (nodes.any { it.id !in canReachOutput }) {
                errors.add(PipelineValidationError.DeadEndNode)
            }
        }

        return errors
    }
}
