package app.knotwork.android.domain.engine.executors

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.PipelineExecutionDefaults
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.CloudLlmModelResolver
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.NetworkActivityTracker
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Executor for [NodeType.CLOUD][app.knotwork.android.domain.models.NodeType.CLOUD] nodes.
 *
 * Streams a response from one of the supported cloud LLM providers (OpenAI, Anthropic,
 * Google, DeepSeek, Ollama) using the Koog client abstraction. The active provider is
 * either taken from `node.cloudProvider` or auto-detected from the first configured API
 * key when the node is set to `"auto"`. The fully assembled `inputText` is sent verbatim:
 * `NodeContextBuilder` has already concatenated the context blocks selected by
 * [NodeContextConfig][app.knotwork.android.domain.models.NodeContextConfig], so the executor
 * must not re-fetch chat history or memory itself.
 *
 * Tokens are forwarded to the orchestrator as
 * [Thinking][app.knotwork.android.domain.models.AgentOrchestratorState.Thinking] /
 * [Answering][app.knotwork.android.domain.models.AgentOrchestratorState.Answering] states
 * during streaming, with the final aggregated text emitted as a
 * [NodeOutput.Result] together with an approximate token count for metrics.
 */
class CloudLlmNodeExecutor @Inject constructor(
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val metricsRepository: MetricsRepository,
    private val cloudLlmClientFactory: CloudLlmClientFactory,
    private val cloudLlmModelResolver: CloudLlmModelResolver,
    private val networkActivityTracker: NetworkActivityTracker,
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
        val nodeSystemPrompt = node.systemPrompt ?: DefaultPrompts.Cloud.SYSTEM_FALLBACK
        val baseSystemPrompt = "$systemPromptPrefix\n$nodeSystemPrompt\n"

        // `inputText` is the assembled context produced upstream by NodeContextBuilder
        // according to the node's NodeContextConfig. Re-fetching chat history or
        // long-term memory here would silently override those flags and break the
        // per-node context-config contract.
        val fullPrompt = "$baseSystemPrompt\n\n$inputText\nAGENT: "

        val startTime = System.currentTimeMillis()

        // `node.cloudProvider` is the persisted UI selection: a real provider id, the
        // sentinel "auto" string, or `null`. `null`/"auto" trigger key-based detection;
        // everything else is parsed through CloudProvider.fromId so legacy aliases
        // (e.g. "gemini") still resolve correctly.
        val configuredProvider = node.cloudProvider
        val selectedProvider: CloudProvider? = if (
            configuredProvider == null || configuredProvider == CloudProvider.AUTO_KEY
        ) {
            autoDetectProvider()
        } else {
            CloudProvider.fromId(configuredProvider)
        }

        val responseStream = if (selectedProvider == null) {
            flowOf("Error: No cloud provider configured or selected")
        } else {
            val client = cloudLlmClientFactory.createClient(selectedProvider) as? LLMClient
            if (client == null) {
                flowOf("Error: ${selectedProvider.id} not configured")
            } else {
                // The resolver owns the per-provider configured-id ↔ default fallback,
                // so the executor stays out of the data layer's settings plumbing.
                val model = cloudLlmModelResolver.resolveModel(selectedProvider) as LLModel
                // Privacy-status pulse for the More tab footer. Recorded right before
                // the network call so the timestamp reflects the actual outbound moment.
                networkActivityTracker.recordOutbound()
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
            Timber.tag(
                "PipelineDebug",
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in CloudLlmNodeExecutor generation")
            emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation")))
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
            return@flow
        }

        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()

        kotlinx.coroutines.delay(PipelineExecutionDefaults.NODE_RESULT_EMIT_DELAY_MS)

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText, tokenCount = approximateTokenCount)))
    }

    /**
     * Picks the first [CloudProvider] for which an API key is configured.
     *
     * Order mirrors the historical "auto" routing priority (Google → Anthropic → OpenAI →
     * DeepSeek) so existing pipelines keep their previous default behaviour. Returns
     * `null` when no provider has credentials, which the caller surfaces to the user as
     * "No cloud provider configured or selected".
     */
    private suspend fun autoDetectProvider(): CloudProvider? {
        if (!apiKeyRepository.getGoogleKey().first().isNullOrBlank()) return CloudProvider.GOOGLE
        if (!apiKeyRepository.getAnthropicKey().first().isNullOrBlank()) return CloudProvider.ANTHROPIC
        if (!apiKeyRepository.getOpenAIKey().first().isNullOrBlank()) return CloudProvider.OPENAI
        if (!apiKeyRepository.getDeepSeekKey().first().isNullOrBlank()) return CloudProvider.DEEPSEEK
        return null
    }
}
