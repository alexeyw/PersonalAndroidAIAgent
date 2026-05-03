package ai.agent.android.domain.prompt

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders prompt templates by substituting `$KEY` placeholders with values produced by the
 * supplied [PromptVariableProvider] instances.
 *
 * A placeholder is any sequence matching the regex `\$([A-Z_][A-Z0-9_]*)`. The leading
 * dollar sign may be escaped with a backslash (`\$KEY`) to emit a literal `$KEY` and skip
 * resolution. Unknown placeholders are kept verbatim and a warning is logged so authors
 * can spot typos. If a provider throws while computing its value, the placeholder is
 * substituted with an empty string and the failure is logged — a single broken provider
 * MUST NOT prevent the rest of the prompt from being rendered.
 *
 * The engine itself is stateless and thread-safe; providers are passed per call to avoid
 * coupling the engine to a fixed Hilt set, which makes ad-hoc previews and tests easier.
 */
@Singleton
class PromptTemplateEngine @Inject constructor() {

    /**
     * Renders [template] by substituting every recognised `$KEY` placeholder with the
     * value resolved by the matching [PromptVariableProvider] from [providers].
     *
     * Implemented on top of [renderSegments] so the two code paths cannot drift apart.
     *
     * @param template raw prompt text that MAY contain placeholders.
     * @param providers list of providers available for substitution. Duplicate keys yield
     * the last-wins semantics of [Map] construction.
     * @return the fully rendered prompt with placeholders replaced (or kept verbatim when
     * unknown / escaped).
     */
    suspend fun render(template: String, providers: List<PromptVariableProvider>): String {
        val segments = renderSegments(template, providers)
        if (segments.isEmpty()) return ""
        val out = StringBuilder(template.length)
        for (segment in segments) {
            when (segment) {
                is PromptSegment.Literal -> out.append(segment.text)
                is PromptSegment.Resolved -> out.append(segment.value)
                is PromptSegment.Unknown -> out.append('$').append(segment.key)
            }
        }
        return out.toString()
    }

    /**
     * Renders [template] into an ordered list of [PromptSegment]s preserving the boundaries
     * between literal text, resolved variables and unknown variables.
     *
     * Adjacent literals are emitted as a single [PromptSegment.Literal] when they are
     * produced from a contiguous run of the source template, but a literal that follows an
     * escaped placeholder may end up split across two segments — the UI does not care since
     * both render the same way.
     *
     * Unlike [render], this entry point exposes enough information to highlight resolved
     * and unknown placeholders separately. It is intended for prompt-preview UIs.
     *
     * @param template raw prompt text that MAY contain placeholders.
     * @param providers list of providers available for substitution. Duplicate keys yield
     * the last-wins semantics of [Map] construction.
     * @return ordered list of segments. Empty list iff [template] is empty.
     */
    suspend fun renderSegments(
        template: String,
        providers: List<PromptVariableProvider>,
    ): List<PromptSegment> {
        if (template.isEmpty()) return emptyList()

        val providerByKey: Map<String, PromptVariableProvider> = buildMap(providers.size) {
            for (provider in providers) {
                // Guard key() the same way we guard resolve(): a single broken provider
                // (e.g. one that throws during lazy initialisation inside key()) MUST NOT
                // prevent the rest of the prompt from being rendered. Skip it and log.
                // CancellationException is rethrown to preserve structured concurrency —
                // even though key() itself is non-suspend, a sub-call could rethrow a
                // cancellation observed elsewhere on the calling coroutine.
                val key = try {
                    provider.key()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Timber.e(t, "Prompt variable provider key() threw; skipping")
                    null
                } ?: continue
                put(key, provider)
            }
        }

        val segments = mutableListOf<PromptSegment>()
        val literalBuffer = StringBuilder()
        var cursor = 0

        fun flushLiteral() {
            if (literalBuffer.isNotEmpty()) {
                segments.add(PromptSegment.Literal(literalBuffer.toString()))
                literalBuffer.setLength(0)
            }
        }

        for (match in PLACEHOLDER_REGEX.findAll(template)) {
            if (match.range.first > cursor) {
                literalBuffer.append(template, cursor, match.range.first)
            }

            val escaped = match.groups[GROUP_ESCAPE]?.value?.isNotEmpty() == true
            val key = match.groups[GROUP_KEY]!!.value

            if (escaped) {
                // \$KEY → literal "$KEY", drop the leading backslash.
                literalBuffer.append('$').append(key)
            } else {
                val provider = providerByKey[key]
                if (provider == null) {
                    Timber.w("Unknown prompt variable: \$%s", key)
                    flushLiteral()
                    segments.add(PromptSegment.Unknown(key))
                } else {
                    // resolve() is suspending I/O. Catch Throwable to render through
                    // a single broken provider, but rethrow CancellationException so the
                    // parent coroutine (e.g. viewModelScope) can cancel this rendering
                    // — swallowing it would break structured concurrency and leak work.
                    val value = try {
                        provider.resolve()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Timber.e(t, "Prompt variable provider \$%s failed", key)
                        ""
                    }
                    flushLiteral()
                    segments.add(PromptSegment.Resolved(key, value))
                }
            }

            cursor = match.range.last + 1
        }

        if (cursor < template.length) {
            literalBuffer.append(template, cursor, template.length)
        }
        flushLiteral()
        return segments
    }

    private companion object {
        /**
         * Captures an optional escaping backslash in group 1 and the variable key in group 2.
         * Key restrictions: must start with an uppercase letter or underscore and may
         * continue with uppercase letters, digits or underscores.
         */
        private val PLACEHOLDER_REGEX = Regex("""(\\?)\$([A-Z_][A-Z0-9_]*)""")
        private const val GROUP_ESCAPE = 1
        private const val GROUP_KEY = 2
    }
}
