package ai.agent.android.domain.prompt

/**
 * One contiguous chunk of a prompt produced by [PromptTemplateEngine.renderSegments].
 *
 * The presentation layer uses these segments to render the prompt with visually distinct
 * styling for resolved and unknown variables (e.g. tinted background for resolved values,
 * red highlight + tooltip for unknown ones).
 *
 * Segments are intentionally a domain concept (no Android imports) so the segmentation
 * logic can be unit-tested in pure JVM and re-used across UI surfaces.
 */
sealed interface PromptSegment {

    /**
     * A run of literal characters from the original template that did not contain any
     * placeholder. Includes characters produced by an escaped placeholder (e.g. `\$KEY`
     * resolves to the literal `$KEY` and lands here).
     */
    data class Literal(
        /** Verbatim characters from the template (or the result of an escaped `\$KEY`). */
        val text: String,
    ) : PromptSegment

    /**
     * A placeholder `$[key]` that was successfully resolved by a matching
     * [PromptVariableProvider].
     *
     * @property key the variable identifier without the leading `$`.
     * @property value the value returned by the provider's `resolve()` call.
     */
    data class Resolved(val key: String, val value: String) : PromptSegment

    /**
     * A placeholder `$[key]` for which no provider was registered. The original
     * `$KEY` text is preserved verbatim by the engine — this segment exists so the UI
     * can highlight it as an authoring error.
     *
     * @property key the variable identifier without the leading `$`.
     */
    data class Unknown(val key: String) : PromptSegment
}
