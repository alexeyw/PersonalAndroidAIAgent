package app.knotwork.android.domain.engine.structured

import app.knotwork.android.domain.constants.DefaultPrompts
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validate-and-repair gate around a single structured-output inference.
 *
 * The agent's autonomy rests on the local 4B model emitting parseable
 * structured output — boolean verdicts, routing keys, sub-task arrays, tool
 * arguments, extracted facts. A malformed reply used to degrade silently
 * (empty fact list, default branch, dropped tool call). This gate replaces that
 * with the proven structured-output pattern: validate the model's output
 * against the requested shape and, on failure, hand the model back its own
 * invalid output together with the validation error and a fixed instruction to
 * correct it — up to a bounded number of repair attempts.
 *
 * The gate is intentionally minimal and pure-domain: it depends only on a
 * [StructuredInferenceClient] (so any engine can back it) and never decides
 * what to do with a [GateResult.Failed] — that per-node policy belongs to the
 * caller. JSON payload extraction (fenced / bare / embedded) is consolidated in
 * [JsonPayloadExtractor]; repair observability is surfaced through
 * [RepairListener].
 */
@Singleton
class StructuredOutputGate @Inject constructor() {

    /**
     * Lenient JSON reader shared by every [runJson] call. `ignoreUnknownKeys`
     * tolerates a model that adds commentary fields; `isLenient` accepts the
     * relaxed quoting local models often produce. Validation still rejects
     * genuinely wrong shapes, which is what drives the repair loop.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Runs an inference expected to yield a JSON value (object **or** array) and
     * deserializes it with [serializer].
     *
     * @param T The target type. For an array form, pass a list serializer
     *   (e.g. `ListSerializer(SubTask.serializer())`).
     * @param inference The engine seam to run the prompt against.
     * @param prompt The fully-rendered initial prompt.
     * @param serializer The `kotlinx.serialization` deserializer the payload
     *   must satisfy.
     * @param nodeName Display name of the calling node (for repair console
     *   lines).
     * @param maxRepairs Maximum corrective re-inferences (clamped to `>= 0`).
     * @param listener Repair observability sink; defaults to [RepairListener.NONE].
     * @return [GateResult.Success] with the parsed value and repair count, or
     *   [GateResult.Failed] carrying the last raw output and parse error.
     */
    suspend fun <T> runJson(
        inference: StructuredInferenceClient,
        prompt: String,
        serializer: KSerializer<T>,
        nodeName: String,
        maxRepairs: Int,
        listener: RepairListener = RepairListener.NONE,
    ): GateResult<T> = runLoop(
        inference = inference,
        originalPrompt = prompt,
        nodeName = nodeName,
        maxRepairs = maxRepairs.coerceAtLeast(0),
        listener = listener,
    ) { raw ->
        val payload = JsonPayloadExtractor.extract(raw)
        try {
            Validation.Valid(json.decodeFromString(serializer, payload))
        } catch (e: IllegalArgumentException) {
            // `decodeFromString` documents two failure modes: `SerializationException`
            // (which itself extends `IllegalArgumentException`) for shape/encoding
            // errors, and a plain `IllegalArgumentException` for cases like an invalid
            // number or an unmappable enum value. Catching the supertype routes *every*
            // format problem into the repair loop instead of letting it tear down the
            // coroutine. This is safe w.r.t. cancellation: `decodeFromString` is
            // non-suspend, so it cannot raise a coroutine `CancellationException` (the
            // only suspend call — `inference.infer` — runs outside this block, and
            // `CancellationException` is an `IllegalStateException`, not caught here).
            Validation.Invalid(e.message ?: "Output did not match the requested JSON schema")
        }
    }

    /**
     * Runs an inference expected to yield exactly one of a constrained set of
     * tokens — e.g. `{"True","False"}`, `{"Pass","Retry","Fail"}`, or the labelled
     * edge keys of an `INTENT_ROUTER`.
     *
     * The first standalone token from [allowed] found anywhere in the output
     * wins (case-insensitive); the returned value is the **canonical** spelling
     * from [allowed], so callers can match it directly against their edge labels
     * without re-normalising case. The token boundary is expressed with
     * alphanumeric-adjacency lookarounds rather than `\b` so a token that begins
     * or ends with a non-word character (e.g. an `INTENT_ROUTER` edge labelled
     * `C#`) still matches, while an incidental substring inside a longer word
     * (`can` in `Cancel`) still does not.
     *
     * @param inference The engine seam to run the prompt against.
     * @param prompt The fully-rendered initial prompt.
     * @param allowed Non-empty set of canonical accepted tokens.
     * @param nodeName Display name of the calling node (for repair console lines).
     * @param maxRepairs Maximum corrective re-inferences (clamped to `>= 0`).
     * @param listener Repair observability sink; defaults to [RepairListener.NONE].
     * @return [GateResult.Success] with the canonical token, or [GateResult.Failed].
     */
    suspend fun runToken(
        inference: StructuredInferenceClient,
        prompt: String,
        allowed: Set<String>,
        nodeName: String,
        maxRepairs: Int,
        listener: RepairListener = RepairListener.NONE,
    ): GateResult<String> {
        require(allowed.isNotEmpty()) { "allowed token set must not be empty" }
        val canonicalByUpper = allowed.associateBy { it.uppercase() }
        val tokenRegex = Regex(
            "(?<![A-Za-z0-9])(${allowed.joinToString("|") { Regex.escape(it) }})(?![A-Za-z0-9])",
            RegexOption.IGNORE_CASE,
        )
        return runLoop(
            inference = inference,
            originalPrompt = prompt,
            nodeName = nodeName,
            maxRepairs = maxRepairs.coerceAtLeast(0),
            listener = listener,
        ) { raw ->
            val match = tokenRegex.find(raw)
            if (match != null) {
                Validation.Valid(canonicalByUpper.getValue(match.value.uppercase()))
            } else {
                Validation.Invalid("Output did not contain one of: ${allowed.joinToString(", ")}")
            }
        }
    }

    /**
     * Shared validate-and-repair loop backing every public entry point.
     *
     * Runs the initial inference at the engine's default temperature, then,
     * while validation fails and the repair budget is not exhausted, re-infers
     * with a [DefaultPrompts.StructuredOutput] feedback prompt at the lowered
     * [REPAIR_TEMPERATURE]. Each repair is announced to [listener] before the
     * corrective inference runs.
     *
     * Repairs are an internal mechanic of one node — they do not consume
     * pipeline steps; they are bounded solely by [maxRepairs].
     *
     * @param validate Pure (non-suspend) validation of one raw output; its
     *   try/catch therefore never wraps a suspend call, keeping the
     *   coroutine-cancellation contract intact.
     */
    private suspend fun <T> runLoop(
        inference: StructuredInferenceClient,
        originalPrompt: String,
        nodeName: String,
        maxRepairs: Int,
        listener: RepairListener,
        validate: (String) -> Validation<T>,
    ): GateResult<T> {
        var prompt = originalPrompt
        var temperature: Float? = null
        var repairs = 0
        while (true) {
            val raw = inference.infer(prompt, temperature)
            when (val outcome = validate(raw)) {
                is Validation.Valid -> return GateResult.Success(outcome.value, repairs)
                is Validation.Invalid -> {
                    if (repairs >= maxRepairs) {
                        return GateResult.Failed(lastRaw = raw, lastError = outcome.error, repairs = repairs)
                    }
                    repairs += 1
                    listener.onRepairAttempt(nodeName, repairs, maxRepairs)
                    prompt = DefaultPrompts.StructuredOutput.repairPrompt(
                        originalPrompt = originalPrompt,
                        previousOutput = raw,
                        error = outcome.error,
                    )
                    temperature = REPAIR_TEMPERATURE
                }
            }
        }
    }

    /**
     * Internal outcome of validating one raw model output: either a parsed
     * value or a human-readable reason the output was rejected.
     */
    private sealed interface Validation<out T> {
        data class Valid<out T>(val value: T) : Validation<T>
        data class Invalid(val error: String) : Validation<Nothing>
    }

    /** Gate-wide tuning constants. */
    companion object {
        /**
         * Sampling temperature used for every repair re-inference. Fixed low so
         * the corrective attempt is near-deterministic and biased towards
         * faithfully reproducing the requested schema rather than re-exploring;
         * the first attempt is left at the caller's configured temperature.
         */
        const val REPAIR_TEMPERATURE: Float = 0.1f
    }
}
