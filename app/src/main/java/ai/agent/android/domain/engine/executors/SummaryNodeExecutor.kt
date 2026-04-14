package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class SummaryNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        val nodeSystemPrompt = node.systemPrompt ?: "You are an AI assistant responsible for summarizing the results of subtasks."
        
        val fullPrompt = "$nodeSystemPrompt\n\nCRITICAL INSTRUCTION: You must synthesize the provided results into a coherent final answer for the original task. Do not just list what each task did. Answer the original task using the data from the results.\n\nORIGINAL TASK: $originalPrompt\n\nRESULTS OF SUBTASKS:\n$inputText\n\nFINAL ANSWER: "
        
        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            val errorMsg = "Error loading local model for summary node"
            emit(AgentOrchestratorState.Error(errorMsg))
            emit(NodeExecutionResult(error = errorMsg))
            return@flow
        }

        val responseStream = llmEngine.generateResponseStream(fullPrompt)
        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        
        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                if (!emittedThinking) {
                    emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                    emittedThinking = true
                } else {
                    emit(AgentOrchestratorState.Answering(accumulatedResponse.toString()))
                }
            }
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in SummaryNodeExecutor generation")
            emit(AgentOrchestratorState.Error(e.message ?: "Unknown error"))
            emit(NodeExecutionResult(error = e.message))
            return@flow
        }
        
        val generatedText = accumulatedResponse.toString().trim()
        val fullResponseText = if (generatedText.isNotEmpty()) generatedText else "No summary generated."
        
        kotlinx.coroutines.delay(1000)
        
        emit(NodeExecutionResult(outputText = fullResponseText))
    }
}