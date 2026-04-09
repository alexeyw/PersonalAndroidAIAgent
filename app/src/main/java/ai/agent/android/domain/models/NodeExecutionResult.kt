package ai.agent.android.domain.models

/**
 * Result of a node execution.
 */
data class NodeExecutionResult(
    val outputText: String? = null,
    val error: String? = null,
    val conditionResult: Boolean? = null,
    val routingKey: String? = null
)