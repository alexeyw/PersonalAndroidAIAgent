package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeModel
import kotlinx.coroutines.flow.Flow

/**
 * Strategy interface for executing a specific node type in the graph.
 */
interface NodeExecutor {
    /**
     * Executes the node logic.
     * @return A Flow emitting [ai.agent.android.domain.models.AgentOrchestratorState] updates and finally a [ai.agent.android.domain.models.NodeExecutionResult].
     */
    fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any>
}