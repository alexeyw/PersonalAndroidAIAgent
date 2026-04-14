package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeType
import javax.inject.Inject

class NodeExecutorFactory @Inject constructor(
    private val inputNodeExecutor: InputNodeExecutor,
    private val outputNodeExecutor: OutputNodeExecutor,
    private val ifConditionNodeExecutor: IfConditionNodeExecutor,
    private val toolNodeExecutor: ToolNodeExecutor,
    private val liteRtNodeExecutor: LiteRtNodeExecutor,
    private val cloudLlmNodeExecutor: CloudLlmNodeExecutor,
    private val systemNodeExecutor: SystemNodeExecutor,
    private val queueProcessorNodeExecutor: QueueProcessorNodeExecutor,
    private val summaryNodeExecutor: SummaryNodeExecutor
) {
    fun getExecutor(type: NodeType): NodeExecutor {
        return when (type) {
            NodeType.INPUT -> inputNodeExecutor
            NodeType.OUTPUT -> outputNodeExecutor
            NodeType.IF_CONDITION -> ifConditionNodeExecutor
            NodeType.TOOL -> toolNodeExecutor
            NodeType.LITE_RT -> liteRtNodeExecutor
            NodeType.CLOUD -> cloudLlmNodeExecutor
            NodeType.INTENT_ROUTER, NodeType.DECOMPOSITION, NodeType.EVALUATION -> systemNodeExecutor
            NodeType.SUMMARY -> summaryNodeExecutor
            NodeType.QUEUE_PROCESSOR -> queueProcessorNodeExecutor
        }
    }
}