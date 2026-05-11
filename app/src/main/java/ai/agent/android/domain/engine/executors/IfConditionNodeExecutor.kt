package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IfConditionNodeExecutor @Inject constructor(private val evaluateIfConditionUseCase: EvaluateIfConditionUseCase) :
    NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = kotlinx.coroutines.flow.flow {
        val isTrue = evaluateIfConditionUseCase(node, inputText)
        emit(NodeOutput.Result(NodeExecutionResult(conditionResult = isTrue, outputText = inputText)))
    }
}
