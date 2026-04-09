package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class OutputNodeExecutor @Inject constructor() : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = kotlinx.coroutines.flow.flow {
        emit(AgentOrchestratorState.Completed(inputText))
        emit(NodeExecutionResult(outputText = inputText))
    }
}