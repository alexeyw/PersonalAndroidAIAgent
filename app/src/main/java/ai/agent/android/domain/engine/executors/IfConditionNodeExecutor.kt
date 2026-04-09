package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class IfConditionNodeExecutor @Inject constructor(
    private val evaluateIfConditionUseCase: EvaluateIfConditionUseCase
) : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = kotlinx.coroutines.flow.flow {
        val isTrue = evaluateIfConditionUseCase(node, inputText)
        emit(NodeExecutionResult(conditionResult = isTrue, outputText = inputText))
    }
}