package app.knotwork.android.domain.engine.structured

/**
 * Outcome of a [StructuredOutputGate] run.
 *
 * The gate never throws on a malformed model output and never silently
 * substitutes a default — it always reports exactly what happened so the
 * **caller** can choose the per-node failure policy (keep a default branch but
 * surface an error, fail the run, or treat the empty result as best-effort).
 * That policy decision is intentionally out of the gate's scope.
 *
 * @param T The validated value type produced on success.
 */
sealed interface GateResult<out T> {

    /**
     * The model produced output that validated against the requested schema /
     * token set.
     *
     * @property value The parsed, schema-valid value.
     * @property repairs Number of repair re-inferences it took to reach this
     *   value (`0` when the very first attempt was already valid). Surfaced so
     *   consumers can record the cost of the node "stumbling" before succeeding.
     */
    data class Success<out T>(val value: T, val repairs: Int) : GateResult<T>

    /**
     * Every attempt — the initial inference plus every allowed repair — failed
     * validation.
     *
     * @property lastRaw The raw model output of the final (still-invalid)
     *   attempt, preserved verbatim for diagnostics / observation logs.
     * @property lastError Human-readable description of why the final attempt
     *   failed validation (deserialization message, or "no recognised token").
     * @property repairs Number of repair re-inferences that were spent before
     *   giving up; equals the effective `maxRepairs` ceiling when it was
     *   non-zero.
     */
    data class Failed(val lastRaw: String, val lastError: String, val repairs: Int) : GateResult<Nothing>
}
