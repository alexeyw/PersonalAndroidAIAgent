package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class OutputNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val chatRepository: ChatRepository,
) : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        if (!node.systemPrompt.isNullOrBlank()) {
            val fullPrompt = "${node.systemPrompt}\n\nCRITICAL INSTRUCTION: Output ONLY the requested format. Do NOT include any conversational filler, explanations, or preambles (e.g., \"Here is the formatted output:\").\n\nINPUT: $inputText\nFORMATTED OUTPUT: "

            val loadResult = loadModelUseCase(node.modelPath)
            if (loadResult is Result.Error) {
                val errorMsg = "Error loading local model for output node"
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
                ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in OutputNodeExecutor generation")
                emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error")))
                emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
                return@flow
            }

            val generatedText = accumulatedResponse.toString().trim()
            var finalOutput = if (generatedText.isNotEmpty()) generatedText else inputText

            // Heuristic cleanup for common LLM conversational fillers
            val lowerCaseOutput = finalOutput.lowercase()
            val prefixesToRemove = listOf(
                "here is the formatted output:",
                "here is the output:",
                "formatted output:",
                "here is the result:",
            )
            for (prefix in prefixesToRemove) {
                if (lowerCaseOutput.startsWith(prefix)) {
                    finalOutput = finalOutput.substring(prefix.length).trim()
                    break
                }
            }

            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.AGENT,
                    content = finalOutput,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            emit(NodeOutput.State(AgentOrchestratorState.Completed(finalOutput)))
            emit(NodeOutput.Result(NodeExecutionResult(outputText = finalOutput)))
        } else {
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.AGENT,
                    content = inputText,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            emit(NodeOutput.State(AgentOrchestratorState.Completed(inputText)))
            emit(NodeOutput.Result(NodeExecutionResult(outputText = inputText)))
        }
    }
}
