package app.knotwork.android.domain.models

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
 * @property tokenCount Number of LLM tokens the node accounted for, or `null` for
 *   non-LLM nodes. Used to attribute token usage per trace step and per node type,
 *   and charged against the run tree's token ceiling. See [tokensEstimated] for
 *   whether the figure is the provider's or the app's own.
 * @property tokensEstimated Whether [tokenCount] is an estimate rather than a
 *   figure the inference provider reported. `true` by default, because most
 *   sources are estimates: local inference counts stream chunks, not model
 *   tokens, and a cloud provider that sends no usage record leaves the executor
 *   counting deltas. Set to `false` only where a real prompt+completion count
 *   was received. The ceiling applies either way; the flag exists so the product
 *   can qualify the number instead of implying a precision it does not have.
 * @property terminationReason For a `PIPELINE` node whose sub-pipeline was ended
 *   by the app itself — a ceiling, most importantly — the typed cause the child
 *   run reported. Carried so the parent engine can re-emit it instead of
 *   flattening the child's decision into prose: without it a ceiling breach one
 *   nesting level down settles the root run as an ordinary failure, which is
 *   exactly the misreading the typed vocabulary exists to prevent. `null` for
 *   every other node and for an ordinary sub-pipeline failure.
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
    val tokensEstimated: Boolean = true,
    val terminationReason: RunTerminationReason? = null,
    val resolvedToolName: String? = null,
)
