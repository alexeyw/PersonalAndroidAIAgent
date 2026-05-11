package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Executor for [NodeType.INPUT][ai.agent.android.domain.models.NodeType.INPUT] nodes.
 *
 * The INPUT node is the entry point of every pipeline run; it simply forwards the user's
 * original message downstream without any transformation. Keeping the implementation as a
 * pass-through executor (rather than special-casing the start node in
 * `GraphExecutionEngine`) lets the engine treat all nodes uniformly through
 * [NodeExecutorFactory].
 */
class InputNodeExecutor @Inject constructor() : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flowOf(NodeOutput.Result(NodeExecutionResult(outputText = inputText)))
}
