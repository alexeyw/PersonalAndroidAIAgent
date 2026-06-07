package app.knotwork.android.domain.prompt

/**
 * Contract for a single substitutable variable that can appear inside a prompt template.
 *
 * Implementations are aggregated into a Hilt multibinding [Set] and consumed by
 * [PromptTemplateEngine] when rendering a template. Each provider is responsible for
 * exposing a unique uppercase key (e.g. `"DATE"`, `"MEMORY_SUMMARY"`) and producing the
 * actual textual value that should replace the placeholder `$KEY` at render time.
 *
 * Providers MUST be safe to call from any coroutine context — heavy work should be
 * offloaded internally via the appropriate dispatcher.
 */
interface PromptVariableProvider {

    /**
     * The unique uppercase identifier of this variable, written WITHOUT the leading `$`.
     *
     * The key MUST match the regex `[A-Z_][A-Z0-9_]*` to be recognised by
     * [PromptTemplateEngine]. Two providers with the same key in the same DI set will
     * lead to undefined resolution order and SHOULD be avoided.
     */
    fun key(): String

    /**
     * Computes the value to substitute in place of `$KEY`.
     *
     * Implementations may perform suspending I/O (database, settings, time-zone lookup,
     * etc.). If this method throws, [PromptTemplateEngine] catches the exception, logs it
     * and substitutes an empty string so a single broken provider cannot break the whole
     * prompt. Returning an empty string explicitly is also valid.
     */
    suspend fun resolve(): String
}
