package app.knotwork.android.domain.models

import java.security.MessageDigest

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
    val updatedAt: Long = System.currentTimeMillis(),
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
    // Reason: validate must accumulate a fixed set of structural checks
    // (missing/multiple INPUT/OUTPUT, dangling edges, cycle, context-config
    // compatibility per NodeType) into a single list. Each branch is one
    // independent rule — extracting helpers would mostly rename, not
    // decompose, the cyclomatic count.
    @Suppress("CyclomaticComplexMethod")
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

        nodes.forEach { node ->
            if (node.usesContextConfig() && node.contextConfig.isEmpty()) {
                errors.add(PipelineValidationError.NodeEmptyContext(node.id))
            }
        }

        return errors
    }

    /**
     * Computes a stable SHA-256 hash over the *execution-relevant* content of
     * the graph. The hash is captured into every persistent pipeline-run
     * record when the run starts and is the checkpoint-invalidation contract:
     * an interrupted run may only be resumed from its persisted node results
     * while the stored hash still equals the current graph's hash — any
     * mismatch means the graph was edited in between and the run must be
     * restarted from scratch.
     *
     * Included: every node field that can influence execution or
     * LLM-visible content (id, type, label — it leaks into tool-result
     * attribution, tool/model/provider bindings, condition fields, system
     * prompt, clarification timeout, context-config flags, per-node config
     * JSON) and every connection (id, endpoints, routing label). Nodes and
     * connections are sorted by id first, so persistence order never affects
     * the hash.
     *
     * Excluded: canvas coordinates ([NodeModel.x] / [NodeModel.y]), the
     * pipeline display [name], [id], and [updatedAt] — moving a node on the
     * canvas or renaming the pipeline must not invalidate a resumable run.
     *
     * @return Lowercase hex encoding of the SHA-256 digest.
     */
    fun contentHash(): String {
        val canonical = buildString {
            nodes.sortedBy { it.id }.forEach { node ->
                append(node.id).append(FIELD_SEPARATOR)
                append(node.type.name).append(FIELD_SEPARATOR)
                append(node.label).append(FIELD_SEPARATOR)
                append(node.toolName.orEmpty()).append(FIELD_SEPARATOR)
                append(node.modelPath.orEmpty()).append(FIELD_SEPARATOR)
                append(node.conditionComplexity?.toString().orEmpty()).append(FIELD_SEPARATOR)
                append(node.conditionKeywords.orEmpty()).append(FIELD_SEPARATOR)
                append(node.conditionPrompt.orEmpty()).append(FIELD_SEPARATOR)
                append(node.systemPrompt.orEmpty()).append(FIELD_SEPARATOR)
                append(node.cloudProvider.orEmpty()).append(FIELD_SEPARATOR)
                append(node.clarificationTimeoutMs?.toString().orEmpty()).append(FIELD_SEPARATOR)
                append(node.contextConfig.toString()).append(FIELD_SEPARATOR)
                append(node.configJson.orEmpty()).append(RECORD_SEPARATOR)
            }
            append(SECTION_SEPARATOR)
            connections.sortedBy { it.id }.forEach { connection ->
                append(connection.id).append(FIELD_SEPARATOR)
                append(connection.sourceNodeId).append(FIELD_SEPARATOR)
                append(connection.targetNodeId).append(FIELD_SEPARATOR)
                append(connection.label.orEmpty()).append(RECORD_SEPARATOR)
            }
        }
        val digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.toHexString()
    }

    private companion object {
        /** Digest algorithm backing [contentHash]. */
        const val HASH_ALGORITHM = "SHA-256"

        /**
         * Unit-separator control character between fields of one record —
         * cannot occur in user-authored prompt text, so concatenated fields
         * can never collide across boundaries.
         */
        const val FIELD_SEPARATOR = '\u001F'

        /** Record-separator control character after each node / connection. */
        const val RECORD_SEPARATOR = '\u001E'

        /** Group-separator control character between the node and connection sections. */
        const val SECTION_SEPARATOR = '\u001D'
    }
}
