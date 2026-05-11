package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class SummaryNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        val nodeSystemPrompt = node.systemPrompt ?: DefaultPrompts.Summary.SYSTEM_FALLBACK

        val fullPrompt = DefaultPrompts.Summary.SYNTHESIS_TEMPLATE
            .replace("\$NODE_SYSTEM_PROMPT", nodeSystemPrompt)
            .replace("\$ORIGINAL_TASK", originalPrompt)
            .replace("\$RESULTS_OF_SUBTASKS", inputText)

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            val errorMsg = "Error loading local model for summary node"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
            return@flow
        }

        val responseStream = llmEngine.generateResponseStream(fullPrompt)
        val accumulatedResponse = StringBuilder()
        var emittedThinking = false

        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                if (!emittedThinking) {
                    emit(NodeOutput.State(AgentOrchestratorState.Thinking(accumulatedResponse.toString())))
                    emittedThinking = true
                } else {
                    emit(NodeOutput.State(AgentOrchestratorState.Answering(accumulatedResponse.toString())))
                }
            }
        } catch (e: CancellationException) {
            // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
            // would silently swallow cancellation and leave the parent coroutine running.
            throw e
        } catch (e: Exception) {
            Timber.tag(
                "PipelineDebug",
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in SummaryNodeExecutor generation")
            emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error")))
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
            return@flow
        }

        val generatedText = accumulatedResponse.toString().trim()
        val fullResponseText = if (generatedText.isNotEmpty()) generatedText else "No summary generated."

        kotlinx.coroutines.delay(1000)

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText)))
    }
}
