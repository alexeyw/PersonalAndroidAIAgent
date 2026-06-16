package app.knotwork.android.domain.engine.structured

import app.knotwork.android.domain.engine.LlmInferenceEngine

/**
 * [StructuredInferenceClient] backed by the local [LlmInferenceEngine].
 *
 * Collapses the engine's token [kotlinx.coroutines.flow.Flow] into the single
 * concatenated string the [StructuredOutputGate] validates, and forwards the
 * optional `temperature` override straight through to
 * [LlmInferenceEngine.generateResponseStream] so the gate's repair re-inferences
 * actually run at the lowered [StructuredOutputGate.REPAIR_TEMPERATURE].
 *
 * The shared [onToken] hook lets a streaming consumer (e.g.
 * [app.knotwork.android.domain.engine.executors.SystemNodeExecutor], which
 * renders `Thinking` progress) keep surfacing live tokens while the gate runs —
 * the gate only ever reads the final concatenated payload, but the underlying
 * generation is still a stream, so nothing is lost by observing it. Consumers
 * that do not stream simply omit the hook (defaults to a no-op).
 *
 * Cancellation is intentionally **not** caught here: a [kotlinx.coroutines.CancellationException]
 * raised by the engine collect must propagate so structured concurrency tears
 * the run down correctly; non-cancellation failures likewise propagate to the
 * calling node, which owns the per-node failure policy.
 *
 * @property engine The local inference engine to run prompts against.
 * @property onToken Invoked for every streamed token before it is appended;
 *   defaults to a no-op for non-streaming callers.
 */
class EngineStructuredInferenceClient(
    private val engine: LlmInferenceEngine,
    private val onToken: suspend (String) -> Unit = {},
) : StructuredInferenceClient {

    /**
     * Runs one (possibly temperature-overridden) generation and returns its full
     * concatenated output.
     *
     * @param prompt The fully-rendered prompt to send to the model.
     * @param temperature Per-call sampling-temperature override, or `null` for
     *   the engine default (forwarded verbatim to the engine).
     * @return The concatenated model output (never `null`; may be empty).
     */
    override suspend fun infer(prompt: String, temperature: Float?): String {
        val builder = StringBuilder()
        engine.generateResponseStream(prompt, temperature).collect { token ->
            builder.append(token)
            onToken(token)
        }
        return builder.toString()
    }
}
