package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import kotlinx.coroutines.flow.Flow

/**
 * Strategy interface for executing a specific node type in the graph.
 */
interface NodeExecutor {
    /**
     * Executes the node logic.
     *
     * @param node The graph node to execute.
     * @param inputText The composed executor input for this node.
     * @param sessionId Id of the chat session the run belongs to.
     * @param originalPrompt The immutable user message that started the run.
     * @param runId Id of the persistent run record, or `null` when the run is
     *   not persisted (e.g. editor test runs). HITL executors use it to key
     *   the parked pending-interaction record of the two-phase waiting
     *   protocol; executors without a persistent waiting phase ignore it.
     * @return A [Flow] of [NodeOutput.State] progress updates terminated by exactly one
     * [NodeOutput.Result] carrying the node's [app.knotwork.android.domain.models.NodeExecutionResult] —
     * except when the run parks in its persistent waiting phase, in which case the flow ends
     * after an [app.knotwork.android.domain.models.AgentOrchestratorState.SuspendedInBackground]
     * state without a result.
     */
    fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String? = null,
    ): Flow<NodeOutput>
}
