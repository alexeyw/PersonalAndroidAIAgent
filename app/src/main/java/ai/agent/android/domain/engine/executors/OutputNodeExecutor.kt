package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class OutputNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase
) : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        if (!node.systemPrompt.isNullOrBlank()) {
            val fullPrompt = "${node.systemPrompt}\n\nINPUT: $inputText\nFORMATTED OUTPUT: "
            
            val loadResult = loadModelUseCase(node.modelPath)
            if (loadResult is Result.Error) {
                val errorMsg = "Error loading local model for output node"
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
                emit(AgentOrchestratorState.Error(e.message ?: "Unknown error"))
                emit(NodeExecutionResult(error = e.message))
                return@flow
            }
            
            val generatedText = accumulatedResponse.toString().trim()
            val finalOutput = if (generatedText.isNotEmpty()) generatedText else inputText
            emit(AgentOrchestratorState.Completed(finalOutput))
            emit(NodeExecutionResult(outputText = finalOutput))
        } else {
            emit(AgentOrchestratorState.Completed(inputText))
            emit(NodeExecutionResult(outputText = inputText))
        }
    }
}