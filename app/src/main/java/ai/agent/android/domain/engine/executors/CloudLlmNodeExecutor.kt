package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.CloudLlmClientFactory
import ai.agent.android.domain.engine.CloudLlmModelResolver
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject

class CloudLlmNodeExecutor @Inject constructor(
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val metricsRepository: MetricsRepository,
    private val cloudLlmClientFactory: CloudLlmClientFactory,
    private val cloudLlmModelResolver: CloudLlmModelResolver,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<NodeOutput> = flow {
        val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
        val nodeSystemPrompt = node.systemPrompt ?: "You are a helpful AI assistant."
        val baseSystemPrompt = "$systemPromptPrefix\n$nodeSystemPrompt\n"

        // `inputText` is the assembled context produced upstream by NodeContextBuilder
        // according to the node's NodeContextConfig. Re-fetching chat history or
        // long-term memory here would silently override those flags and break Phase 15.
        val fullPrompt = "$baseSystemPrompt\n\n$inputText\nAGENT: "

        val startTime = System.currentTimeMillis()

        var selectedProvider = node.cloudProvider ?: "auto"
        if (selectedProvider == "auto") {
            val googleKey = apiKeyRepository.getGoogleKey().first()
            val anthropicKey = apiKeyRepository.getAnthropicKey().first()
            val openAIKey = apiKeyRepository.getOpenAIKey().first()
            val deepSeekKey = apiKeyRepository.getDeepSeekKey().first()

            selectedProvider = when {
                !googleKey.isNullOrBlank() -> "google"
                !anthropicKey.isNullOrBlank() -> "anthropic"
                !openAIKey.isNullOrBlank() -> "openai"
                !deepSeekKey.isNullOrBlank() -> "deepseek"
                else -> "none"
            }
        }

        val responseStream = if (selectedProvider == "none") {
            flowOf("Error: No cloud provider configured or selected")
        } else {
            val client = cloudLlmClientFactory.createClient(selectedProvider) as? LLMClient
            if (client == null) {
                flowOf("Error: ${selectedProvider} not configured")
            } else {
                // The resolver owns the per-provider configured-id ↔ default fallback,
                // so the executor stays out of the data layer's settings plumbing.
                val model = cloudLlmModelResolver.resolveModel(selectedProvider) as LLModel
                client.executeStreaming(prompt("default") { user(fullPrompt) }, model)
                    .mapNotNull { (it as? StreamFrame.TextDelta)?.text }
            }
        }

        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        var approximateTokenCount = 0

        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                // Cloud streams emit token-sized text deltas; counting per emission keeps the
                // metric consistent with the local LiteRT path. Length-based estimation was
                // double-counting characters and inflating the recorded token total.
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
            Timber.tag("PipelineDebug").e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in CloudLlmNodeExecutor generation")
            emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation")))
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
            return@flow
        }

        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()

        kotlinx.coroutines.delay(1000)

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText, tokenCount = approximateTokenCount)))
    }
}
