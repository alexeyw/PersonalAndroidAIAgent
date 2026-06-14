package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Executor for [NodeType.QUEUE_PROCESSOR][app.knotwork.android.domain.models.NodeType.QUEUE_PROCESSOR]
 * nodes.
 *
 * Queue-processor nodes are routing markers consumed by `GraphExecutionEngine` itself —
 * the engine handles task-queue draining around them. The executor therefore acts as a
 * pure pass-through, echoing the upstream text downstream without touching the queue
 * state directly.
 */
class QueueProcessorNodeExecutor @Inject constructor() : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        depth: Int,
    ): Flow<NodeOutput> = flowOf(NodeOutput.Result(NodeExecutionResult(outputText = inputText)))
}
