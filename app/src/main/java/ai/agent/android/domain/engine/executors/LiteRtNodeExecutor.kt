package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import javax.inject.Inject

class LiteRtNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
    private val settingsRepository: SettingsRepository,
    private val metricsRepository: MetricsRepository,
    private val loadModelUseCase: LoadModelUseCase
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
        val nodeSystemPrompt = node.systemPrompt ?: "You are a helpful AI assistant."
        val baseSystemPrompt = "$systemPromptPrefix\n$nodeSystemPrompt\n"

        val contextWindow = getContextWindowUseCase(sessionId)
        
        val relevantMemories = retrieveRelevantMemoryUseCase(originalPrompt)
        val memoryContext = if (relevantMemories.isNotEmpty()) {
            "RELEVANT LONG-TERM MEMORIES:\n" + relevantMemories.joinToString("\n") { "- ${it.text}" } + "\n\n"
        } else {
            ""
        }

        val fullPrompt = "$baseSystemPrompt\n\n$memoryContext$contextWindow\n\nUSER/INPUT: $inputText\nAGENT: "
        
        val startTime = System.currentTimeMillis()
        
        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            emit(AgentOrchestratorState.Error("Error loading local model"))
            emit(NodeExecutionResult(error = "Error loading local model"))
            return@flow
        }

        val responseStream = llmEngine.generateResponseStream(fullPrompt)
        
        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        var approximateTokenCount = 0

        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                approximateTokenCount += token.length / 4 + 1
                
                if (!emittedThinking) {
                    emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                    emittedThinking = true
                } else {
                    emit(AgentOrchestratorState.Answering(accumulatedResponse.toString()))
                }
            }
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in LiteRtNodeExecutor generation")
            emit(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation"))
            emit(NodeExecutionResult(error = e.message))
            return@flow
        }
        
        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()
        
        kotlinx.coroutines.delay(1000)

        emit(NodeExecutionResult(outputText = fullResponseText))
    }
}