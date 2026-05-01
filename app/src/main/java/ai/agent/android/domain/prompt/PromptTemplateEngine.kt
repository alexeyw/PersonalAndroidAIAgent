package ai.agent.android.domain.prompt

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
     * @param template raw prompt text that MAY contain placeholders.
     * @param providers list of providers available for substitution. Duplicate keys yield
     * the last-wins semantics of [Map] construction.
     * @return the fully rendered prompt with placeholders replaced (or kept verbatim when
     * unknown / escaped).
     */
    suspend fun render(template: String, providers: List<PromptVariableProvider>): String {
        if (template.isEmpty()) return template

        val providerByKey: Map<String, PromptVariableProvider> = providers.associateBy { it.key() }
        val out = StringBuilder(template.length)
        var cursor = 0

        for (match in PLACEHOLDER_REGEX.findAll(template)) {
            // Append everything between the previous match and this one verbatim.
            if (match.range.first > cursor) {
                out.append(template, cursor, match.range.first)
            }

            val escaped = match.groups[GROUP_ESCAPE]?.value?.isNotEmpty() == true
            val key = match.groups[GROUP_KEY]!!.value

            if (escaped) {
                // \$KEY → literal "$KEY", drop the leading backslash.
                out.append('$').append(key)
            } else {
                val provider = providerByKey[key]
                if (provider == null) {
                    Timber.w("Unknown prompt variable: \$%s", key)
                    out.append('$').append(key)
                } else {
                    val value = runCatching { provider.resolve() }
                        .onFailure { Timber.e(it, "Prompt variable provider \$%s failed", key) }
                        .getOrDefault("")
                    out.append(value)
                }
            }

            cursor = match.range.last + 1
        }

        if (cursor < template.length) {
            out.append(template, cursor, template.length)
        }
        return out.toString()
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
