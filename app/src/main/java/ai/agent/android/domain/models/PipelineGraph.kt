package ai.agent.android.domain.models

/**
 * Represents a complete pipeline graph composed of nodes and directed connections.
 * Forms a Directed Acyclic Graph (DAG) for execution.
 *
 * @property id The unique identifier of the pipeline.
 * @property name The display name of the pipeline.
 * @property nodes The list of [NodeModel]s present in the pipeline.
 * @property connections The list of [ConnectionModel]s linking the nodes.
 */
data class PipelineGraph(
    val id: String,
    val name: String,
    val nodes: List<NodeModel> = emptyList(),
    val connections: List<ConnectionModel> = emptyList()
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
            if (adjacencyList.containsKey(connection.sourceNodeId)) {
                adjacencyList[connection.sourceNodeId]?.add(connection.targetNodeId)
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
}
