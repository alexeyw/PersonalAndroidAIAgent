package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.usecases.EvaluateIfConditionUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Executor for [NodeType.IF_CONDITION][app.knotwork.android.domain.models.NodeType.IF_CONDITION]
 * branching nodes.
 *
 * Delegates the boolean evaluation to [EvaluateIfConditionUseCase] (keyword match,
 * complexity heuristics, or LLM-based judgement depending on the node's configuration)
 * and forwards the upstream `inputText` unchanged so that downstream branches still see
 * the original payload. The branch decision is published via
 * [NodeExecutionResult.conditionResult][app.knotwork.android.domain.models.NodeExecutionResult.conditionResult],
 * which `GraphExecutionEngine` reads to pick the next connection.
 */
class IfConditionNodeExecutor @Inject constructor(private val evaluateIfConditionUseCase: EvaluateIfConditionUseCase) :
    NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        depth: Int,
    ): Flow<NodeOutput> = kotlinx.coroutines.flow.flow {
        val isTrue = evaluateIfConditionUseCase(node, inputText)
        emit(NodeOutput.Result(NodeExecutionResult(conditionResult = isTrue, outputText = inputText)))
    }
}
