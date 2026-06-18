package app.knotwork.android.domain.engine.structured

/**
 * Narrow inference seam the [StructuredOutputGate] sits on top of.
 *
 * The gate needs only one capability from the underlying model: run a single
 * prompt to completion and hand back the full text. It deliberately does **not**
 * depend on [app.knotwork.android.domain.engine.LlmInferenceEngine] directly so
 * that:
 *  - the gate stays pure-domain and trivially unit-testable with a fake;
 *  - the caller decides which engine (local LiteRT or a cloud provider) backs a
 *    given gate invocation, and how the optional [temperature] override is
 *    actually applied to that engine (the engine-side temperature plumbing is
 *    wired by the gate's consumers, not by the gate).
 *
 * Implementations collapse a streaming engine into a single concatenated string
 * (the gate only validates the final payload, never intermediate tokens).
 */
fun interface StructuredInferenceClient {

    /**
     * Runs one inference and returns the model's complete output.
     *
     * @param prompt The fully-rendered prompt to send to the model.
     * @param temperature Sampling-temperature override for this single call, or
     *   `null` to use the caller's configured default. The gate passes
     *   [StructuredOutputGate.REPAIR_TEMPERATURE] on repair re-inferences so the
     *   model is nudged towards a more deterministic, schema-obedient retry, and
     *   `null` on the first attempt so normal generation is unaffected.
     * @return The concatenated model output (never `null`; an empty string is a
     *   valid — and validation-failing — result).
     */
    suspend fun infer(prompt: String, temperature: Float?): String
}
