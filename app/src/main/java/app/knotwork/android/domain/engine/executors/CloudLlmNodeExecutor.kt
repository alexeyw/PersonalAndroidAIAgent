package app.knotwork.android.domain.engine.executors

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.PipelineExecutionDefaults
import app.knotwork.android.domain.engine.CloudErrorSanitizer
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.CloudLlmModelResolver
import app.knotwork.android.domain.engine.retry.CollectingCloudRetryListener
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.ExecutionScope
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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
 *
 * **Privacy contract — attachments never reach the cloud.** A run's image
 * attachment is delivered by [GraphExecutionEngine][app.knotwork.android.domain.engine.GraphExecutionEngine]
 * exclusively to an on-device `LITE_RT` node (via [ExecutionScope.imagePath]).
 * This executor deliberately ignores [ExecutionScope.imagePath] and only ever
 * sends the assembled text `inputText`, so a user's image is never transmitted
 * off-device. The send-time pre-flight guard blocks an image message whose
 * pipeline starts on a CLOUD node before it can run.
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
        scope: ExecutionScope,
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

        // Buffers transient-failure retries performed by the Koog retry policy so
        // each one can be surfaced as a console line after the stream drains
        // (symmetric to the structured-output repair lines). Retries happen before
        // the first token, so draining afterwards still reports them in order.
        val retryCollector = CollectingCloudRetryListener()

        // A node that cannot reach a provider has *failed*; it has not answered.
        // Feeding the explanation into the token stream (as this executor used to do)
        // made `NodeExecutionResult.error` stay null, so the engine read the run as a
        // success and passed the sentence "Error: … not configured" downstream as if it
        // were the model's reply. Configuration failures therefore terminate the node
        // through the same typed-error path as a mid-stream exception.
        if (selectedProvider == null) {
            emitFailure(NO_PROVIDER_CONFIGURED)
            return@flow
        }
        val client = cloudLlmClientFactory.createClient(selectedProvider, retryCollector) as? LLMClient
        if (client == null) {
            // The factory collapses "blocked by policy" and "no credentials" into a
            // single null, but the two need different remedies from the user, so the
            // gate is re-read here to name the actual cause instead of always blaming
            // a missing key.
            emitFailure(
                if (settingsRepository.blockNetworkFromLocalModel.first()) {
                    cloudBlockedByLocalOnlyMode(selectedProvider.id)
                } else {
                    missingCredentials(selectedProvider.id)
                },
            )
            return@flow
        }
        // The resolver owns the per-provider configured-id ↔ default fallback,
        // so the executor stays out of the data layer's settings plumbing.
        val model = cloudLlmModelResolver.resolveModel(selectedProvider) as LLModel
        // Privacy-status pulse for the More tab footer. Recorded right before
        // the network call so the timestamp reflects the actual outbound moment.
        networkActivityTracker.recordOutbound()
        val responseStream = client.executeStreaming(prompt("default") { user(fullPrompt) }, model)

        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        var approximateTokenCount = 0
        // Whether the provider ever said *why* it stopped. A dropped connection ends the
        // stream with no finish reason, which is otherwise indistinguishable from a
        // complete answer — see [providerReportsFinishReason].
        var finishReason: String? = null

        try {
            responseStream.collect { frame ->
                if (frame is StreamFrame.End) {
                    finishReason = frame.finishReason
                    return@collect
                }
                val token = (frame as? StreamFrame.TextDelta)?.text ?: return@collect
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
            // Provider errors quote the failing request, and a provider that authenticates
            // by query parameter (Google) therefore hands us its own API key inside the
            // message. Scrub before it reaches the console, the trace or logcat — passing
            // the message rather than the throwable to Timber keeps the key out of the
            // logged stack trace too.
            val safeMessage = CloudErrorSanitizer.sanitize(e.message)
            Timber.tag(
                "PipelineDebug",
            ).e("[NODE_ERR] type=${node.type.name} id=${node.id} CloudLlmNodeExecutor generation: $safeMessage")
            emitRetries(retryCollector)
            emit(NodeOutput.State(AgentOrchestratorState.Error(safeMessage)))
            emit(NodeOutput.Result(NodeExecutionResult(error = safeMessage)))
            return@flow
        }

        // A provider whose connection dies mid-answer does not always raise: the
        // OpenAI-compatible clients end the stream normally and simply omit the finish
        // reason, so the half-written answer would otherwise be handed on as a complete
        // one. Measured on a stub that cut the socket mid-stream: identical frames in both
        // cases apart from `finishReason` (null when cut, "stop" when complete).
        if (providerReportsFinishReason(selectedProvider) && finishReason == null) {
            emitRetries(retryCollector)
            emitFailure(truncatedResponse(selectedProvider.id))
            return@flow
        }

        emitRetries(retryCollector)

        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()

        // Record the cloud provider as the answering "model" so the root OUTPUT
        // attributes the message to the cloud source rather than to whatever local
        // model happens to be active. selectedProvider is non-null on this success
        // path (a null provider returns early above).
        selectedProvider?.let { provider ->
            scope.generatingModel?.let { gm ->
                gm.cloudLabel = provider.id
                gm.localModelPath = null
            }
        }

        kotlinx.coroutines.delay(PipelineExecutionDefaults.NODE_RESULT_EMIT_DELAY_MS)

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText, tokenCount = approximateTokenCount)))
    }

    /**
     * Terminates the node with a typed failure, using the same two-emission shape as
     * the mid-stream exception path: an [AgentOrchestratorState.Error] for the live UI
     * and a [NodeExecutionResult] carrying `error`, which is what
     * [GraphExecutionEngine][app.knotwork.android.domain.engine.GraphExecutionEngine]
     * inspects to stop the run instead of forwarding the text to the next node.
     *
     * @param reason User-facing explanation of why no cloud call was attempted.
     */
    private suspend fun FlowCollector<NodeOutput>.emitFailure(reason: String) {
        Timber.tag("PipelineDebug").e("[NODE_ERR] type=CLOUD $reason")
        emit(NodeOutput.State(AgentOrchestratorState.Error(reason)))
        emit(NodeOutput.Result(NodeExecutionResult(error = reason)))
    }

    /**
     * Emits one [NodeOutput.Console] per buffered transient-failure retry, so the
     * user can see the cloud provider blipped before recovering.
     *
     * @param collector The listener that buffered the retries during the call.
     */
    private suspend fun FlowCollector<NodeOutput>.emitRetries(collector: CollectingCloudRetryListener) {
        collector.attempts.forEach { attempt ->
            emit(NodeOutput.Console(ConsoleEventType.CloudRetry, attempt.consoleMessage()))
        }
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

    /**
     * Whether an absent finish reason is evidence of a truncated answer for [provider].
     *
     * Only true where it was actually measured against a stub, because the inverse
     * mistake — treating a healthy stream as truncated — fails working runs:
     *
     * - `DEEPSEEK` / `OPENAI` — **checked**. Measured on DeepSeek: a socket cut mid-stream
     *   ends the flow with `End(finishReason = null)` and no exception, while a complete
     *   stream ends with `End(finishReason = "stop")`. OpenAI shares the same streaming
     *   implementation (`AbstractOpenAILLMClient`), so the signal is the same one.
     * - `GOOGLE` — **checked**, and already correct without help: its client throws
     *   `IncompleteStreamException` on a cut, so the guard below never fires for it.
     * - `OLLAMA` — **excluded**. Its client never emits a finish reason at all, so absence
     *   carries no information; enabling the check would fail every healthy Ollama run.
     * - `ANTHROPIC` — **excluded pending measurement**. The harness could not produce a
     *   stream its parser accepts, so there is no evidence either way, and a guess here
     *   is exactly the failure mode this task exists to avoid.
     */
    private fun providerReportsFinishReason(provider: CloudProvider): Boolean = when (provider) {
        CloudProvider.OPENAI, CloudProvider.DEEPSEEK, CloudProvider.GOOGLE -> true
        CloudProvider.ANTHROPIC, CloudProvider.OLLAMA -> false
    }

    private companion object {
        /** The provider stopped sending without ever saying the answer was finished. */
        fun truncatedResponse(providerId: String): String =
            "The response from '$providerId' was cut off before it finished — the connection " +
                "ended without a completion signal. The partial answer was discarded; try again."

        /** No provider is selected on the node and no configured key could be auto-detected. */
        const val NO_PROVIDER_CONFIGURED: String =
            "No cloud provider is configured. Add a provider API key in Settings, " +
                "or select a provider on this Cloud node."

        /**
         * The node names a provider whose credentials are missing — distinct from the
         * policy block below, which the user resolves in a different place entirely.
         */
        fun missingCredentials(providerId: String): String =
            "Cloud provider '$providerId' has no API key configured. Add one in Settings to use this node."

        /** The call never left the device because the local-only restriction is on. */
        fun cloudBlockedByLocalOnlyMode(providerId: String): String =
            "Cloud provider '$providerId' is blocked by the \"Block network from local model\" " +
                "restriction. Turn it off in Settings to allow this Cloud node to run."
    }
}
