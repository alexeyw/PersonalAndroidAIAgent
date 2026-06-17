package app.knotwork.android.data.engine.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import app.knotwork.android.domain.engine.retry.CloudRetryListener
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pass-through [LLMClient] decorator that observes retries performed by an
 * **outer** [ai.koog.prompt.executor.clients.retry.RetryingLLMClient].
 *
 * Koog's `RetryingLLMClient` re-invokes its delegate once per attempt but
 * exposes no per-attempt callback, so this decorator is placed *as that
 * delegate*: it forwards every call to the real [delegate] verbatim and counts
 * how many times the wrapped operation is invoked. The first invocation is the
 * initial call; every subsequent invocation is a retry, reported through
 * [listener] as `onRetry(provider, attempt = n - 1, maxRetries = maxAttempts - 1)`.
 *
 * **Single-operation contract.** The invocation counter is shared across all
 * operations of one instance, so a given instance must serve exactly one
 * logical operation (one streaming generation, or one embedding request). This
 * holds for every caller in the project: the cloud client / embedding factories
 * build a fresh client per call. Reusing one instance for several operations
 * would conflate their attempt counts.
 *
 * @property delegate The real cloud client every call is forwarded to.
 * @property provider Display id of the cloud provider (e.g. `"openai"`), passed
 *   to [listener] for the console line.
 * @property maxAttempts The configured attempt budget (initial call + retries),
 *   used to derive the retry ceiling reported to [listener].
 * @property listener Sink notified before each retry.
 */
internal class RetryObservingLLMClient(
    private val delegate: LLMClient,
    private val provider: String,
    private val maxAttempts: Int,
    private val listener: CloudRetryListener,
) : LLMClient() {

    private val invocations = AtomicInteger(0)

    /**
     * Records one invocation of the wrapped operation and reports it as a retry
     * when it is not the first.
     */
    private fun recordInvocation() {
        val n = invocations.incrementAndGet()
        if (n > 1) {
            listener.onRetry(provider = provider, attempt = n - 1, maxRetries = maxAttempts - 1)
        }
    }

    override fun llmProvider(): LLMProvider = delegate.llmProvider()

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        recordInvocation()
        return delegate.execute(prompt, model, tools)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> {
        // The outer RetryingLLMClient re-invokes this function (rebuilding the
        // flow) once per attempt, after its backoff delay — so counting here
        // fires the listener exactly when a retry is about to run.
        recordInvocation()
        return delegate.executeStreaming(prompt, model, tools)
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice = delegate.executeMultipleChoices(prompt, model, tools)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = delegate.moderate(prompt, model)

    override suspend fun models(): List<LLModel> = delegate.models()

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        recordInvocation()
        return delegate.embed(text, model)
    }

    override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> {
        recordInvocation()
        return delegate.embed(inputs, model)
    }

    override fun close() = delegate.close()

    override fun getStandardJsonSchemaGenerator(): StandardJsonSchemaGenerator =
        delegate.getStandardJsonSchemaGenerator()

    override fun getBasicJsonSchemaGenerator(): BasicJsonSchemaGenerator = delegate.getBasicJsonSchemaGenerator()
}
