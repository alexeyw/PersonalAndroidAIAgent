package ai.agent.android.domain.models

/**
 * Result of a node execution.
 *
 * @property outputText The text produced by the node, if any.
 * @property error Optional error message if the node failed.
 * @property conditionResult For IF-condition nodes, the evaluated boolean.
 * @property routingKey Branch selector for routing node types: for
 *   [NodeType.INTENT_ROUTER] the full model response (matched against
 *   class-named edges); for [NodeType.EVALUATION] the canonical verdict label
 *   (`"Pass"` / `"Retry"` / `"Fail"`) used to pick the matching output port.
 *   `null` for non-routing nodes.
 * @property tokenCount Approximate number of LLM tokens produced by the node, or `null`
 *   for non-LLM nodes. Used to attribute token usage per trace step and per node type.
 * @property resolvedToolName For TOOL nodes only — the actual tool that was selected
 *   and executed. When the node is configured with `toolName = "auto"` the executor
 *   resolves the concrete tool dynamically; the engine reads this field to record
 *   the real attribution in `ToolInvocationResult`. `null` for non-TOOL nodes.
 */
data class NodeExecutionResult(
    val outputText: String? = null,
    val error: String? = null,
    val conditionResult: Boolean? = null,
    val routingKey: String? = null,
    val tokenCount: Int? = null,
    val resolvedToolName: String? = null,
)
