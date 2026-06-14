package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Executor for [NodeType.LITE_RT][app.knotwork.android.domain.models.NodeType.LITE_RT] nodes.
 *
 * Runs inference on the local LiteRT engine: ensures the requested model is loaded (via
 * [LoadModelUseCase]), builds the full prompt from the node's system prompt plus the
 * upstream `inputText` produced by `NodeContextBuilder`, and streams tokens back as
 * orchestrator state updates. The final response is emitted as a
 * [NodeOutput.Result] together with the per-token count used by the metrics repository.
 *
 * Cancellation is rethrown explicitly so that structured concurrency keeps working — a
 * broad `catch (Exception)` would otherwise swallow it and leak coroutines.
 */
class LiteRtNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val metricsRepository: MetricsRepository,
    private val loadModelUseCase: LoadModelUseCase,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        depth: Int,
    ): Flow<NodeOutput> = flow {
        val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
        val nodeSystemPrompt = node.systemPrompt ?: DefaultPrompts.LiteRt.SYSTEM_FALLBACK
        val baseSystemPrompt = "$systemPromptPrefix\n$nodeSystemPrompt\n"

        // `inputText` is the assembled context produced upstream by NodeContextBuilder
        // according to the node's NodeContextConfig. Re-fetching chat history or
        // long-term memory here would silently override those flags and break the
        // per-node context-config contract.
        val fullPrompt = "$baseSystemPrompt\n\n$inputText\nAGENT: "

        val startTime = System.currentTimeMillis()

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            emit(NodeOutput.State(AgentOrchestratorState.Error("Error loading local model")))
            emit(NodeOutput.Result(NodeExecutionResult(error = "Error loading local model")))
            return@flow
        }

        val responseStream = llmEngine.generateResponseStream(fullPrompt)

        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        var approximateTokenCount = 0

        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                // Each emitted token from LiteRT is one model token, not a string of arbitrary
                // length — counting by `+= 1` matches the real generation count.
                approximateTokenCount += 1

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
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in LiteRtNodeExecutor generation")
            emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation")))
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
            return@flow
        }

        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText, tokenCount = approximateTokenCount)))
    }
}
