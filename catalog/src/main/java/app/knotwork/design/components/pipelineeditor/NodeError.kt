package app.knotwork.design.components.pipelineeditor

/**
 * Error state surfaced on a [NodeCard]'s outer border + body. Either
 * `null` (idle) or one of the two variants below.
 *
 * The variants are deliberately thin: rendering treats both as a 2 dp
 * `signalError` border, and the body differs only by which error glyph /
 * message it surfaces. Keeping the type sealed (instead of a `String?`)
 * lets a future addition (e.g. `Authorization`) slot in without rippling
 * through callers.
 */
sealed interface NodeError {
    /** Human-readable error message rendered under the title. */
    val message: String

    /**
     * Configuration / validation failure surfaced at design time
     * (e.g. unbound port, invalid expression).
     *
     * @property message the inline error message.
     */
    data class Validation(override val message: String) : NodeError

    /**
     * Runtime failure surfaced during pipeline execution
     * (e.g. tool call threw, LLM timed out).
     *
     * @property message the inline cause string.
     */
    data class Runtime(override val message: String) : NodeError
}
