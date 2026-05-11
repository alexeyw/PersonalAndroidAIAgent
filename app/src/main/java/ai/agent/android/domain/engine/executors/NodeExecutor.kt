package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import kotlinx.coroutines.flow.Flow

/**
 * Strategy interface for executing a specific node type in the graph.
 */
interface NodeExecutor {
    /**
     * Executes the node logic.
     *
     * @return A [Flow] of [NodeOutput.State] progress updates terminated by exactly one
     * [NodeOutput.Result] carrying the node's [ai.agent.android.domain.models.NodeExecutionResult].
     */
    fun execute(node: NodeModel, inputText: String, sessionId: String, originalPrompt: String): Flow<NodeOutput>
}
