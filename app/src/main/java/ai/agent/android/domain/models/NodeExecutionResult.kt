package ai.agent.android.domain.models

/**
 * Result of a node execution.
 *
 * @property outputText The text produced by the node, if any.
 * @property error Optional error message if the node failed.
 * @property conditionResult For IF-condition nodes, the evaluated boolean.
 * @property routingKey For intent-router nodes, the matched routing key.
 * @property tokenCount Approximate number of LLM tokens produced by the node, or `null`
 *   for non-LLM nodes. Used to attribute token usage per trace step and per node type.
 */
data class NodeExecutionResult(
    val outputText: String? = null,
    val error: String? = null,
    val conditionResult: Boolean? = null,
    val routingKey: String? = null,
    val tokenCount: Int? = null,
)
