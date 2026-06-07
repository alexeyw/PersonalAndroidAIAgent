package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.NodeType
import javax.inject.Inject

/**
 * Hilt-injected lookup table that maps every [NodeType] to the concrete [NodeExecutor]
 * implementation responsible for running nodes of that type.
 *
 * The factory is the single dispatch point used by `GraphExecutionEngine`: the engine
 * does not know about individual executors, it just asks the factory for the executor
 * matching the current node's [NodeType]. Adding a new node type therefore requires
 * (1) extending the [NodeType] enum and (2) wiring a new branch in [getExecutor].
 *
 * Several `NodeType`s are intentionally collapsed onto the same executor — for example,
 * `INTENT_ROUTER`, `DECOMPOSITION`, and `EVALUATION` all delegate to [SystemNodeExecutor]
 * because they share the same "LLM with a system prompt, no tool/streaming side effects"
 * shape.
 */
class NodeExecutorFactory @Inject constructor(
    private val inputNodeExecutor: InputNodeExecutor,
    private val outputNodeExecutor: OutputNodeExecutor,
    private val ifConditionNodeExecutor: IfConditionNodeExecutor,
    private val toolNodeExecutor: ToolNodeExecutor,
    private val liteRtNodeExecutor: LiteRtNodeExecutor,
    private val cloudLlmNodeExecutor: CloudLlmNodeExecutor,
    private val systemNodeExecutor: SystemNodeExecutor,
    private val queueProcessorNodeExecutor: QueueProcessorNodeExecutor,
    private val summaryNodeExecutor: SummaryNodeExecutor,
    private val clarificationNodeExecutor: ClarificationNodeExecutor,
) {
    /**
     * Returns the [NodeExecutor] responsible for nodes of the given [type].
     *
     * @param type the [NodeType] of the node about to be executed.
     * @return the concrete executor; the `when` is exhaustive, so every [NodeType] is
     *   guaranteed to be routed.
     */
    fun getExecutor(type: NodeType): NodeExecutor = when (type) {
        NodeType.INPUT -> inputNodeExecutor
        NodeType.OUTPUT -> outputNodeExecutor
        NodeType.IF_CONDITION -> ifConditionNodeExecutor
        NodeType.TOOL -> toolNodeExecutor
        NodeType.LITE_RT -> liteRtNodeExecutor
        NodeType.CLOUD -> cloudLlmNodeExecutor
        NodeType.INTENT_ROUTER, NodeType.DECOMPOSITION, NodeType.EVALUATION -> systemNodeExecutor
        NodeType.SUMMARY -> summaryNodeExecutor
        NodeType.QUEUE_PROCESSOR -> queueProcessorNodeExecutor
        NodeType.CLARIFICATION -> clarificationNodeExecutor
    }
}
