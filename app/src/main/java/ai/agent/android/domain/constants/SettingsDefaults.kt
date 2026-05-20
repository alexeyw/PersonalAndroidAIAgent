package ai.agent.android.domain.constants

/**
 * Canonical default values for every user-tunable setting that lives behind
 * [ai.agent.android.domain.repositories.SettingsRepository].
 *
 * Each `const val` is the **single source of truth** consumed by:
 *  - the DataStore-backed implementation
 *    ([ai.agent.android.data.local.SettingsManager]) when a preference key has
 *    not yet been written;
 *  - the settings UI (`SettingsViewModel` / `SettingsScreen`) for slider bounds
 *    and reset-to-default actions;
 *  - the Ollama provider auto-detection path
 *    ([ai.agent.android.data.local.ApiKeyManager]) when the user has not
 *    overridden the context-window size for a given model;
 *  - the visual orchestrator node-editor when seeding the timeout field of a
 *    fresh `CLARIFICATION` node.
 *
 * Centralising the defaults here keeps the production code, tests, and UI in
 * lock-step: bumping a default in one place automatically updates every caller.
 */
object SettingsDefaults {
    /** Maximum number of tokens to keep in the LLM context window by default. */
    const val MAX_CONTEXT_LENGTH_DEFAULT: Int = 4_000

    /** Default sampling temperature for local LLM generation. */
    const val TEMPERATURE_DEFAULT: Float = 0.7f

    /** Default `top-k` sampling parameter for local LLM generation. */
    const val TOP_K_DEFAULT: Int = 40

    /** Default `top-p` (nucleus sampling) parameter for local LLM generation. */
    const val TOP_P_DEFAULT: Float = 0.9f

    /** Default wall-clock timeout for a single tool invocation, in milliseconds. */
    const val TOOL_CALL_TIMEOUT_MS_DEFAULT: Long = 60_000L

    /**
     * Default wall-clock timeout for a `CLARIFICATION` node's outstanding question,
     * in milliseconds. Mirrors [TOOL_CALL_TIMEOUT_MS_DEFAULT] today but is exposed
     * as a separate constant so the two can diverge without code-wide impact.
     */
    const val CLARIFICATION_TIMEOUT_MS_DEFAULT: Long = 60_000L

    /** Default maximum number of pipeline steps allowed per user request. */
    const val PIPELINE_MAX_STEPS_DEFAULT: Int = 15

    /** Lower bound enforced when the user edits the pipeline-max-steps setting. */
    const val PIPELINE_MAX_STEPS_MIN: Int = 5

    /** Upper bound enforced when the user edits the pipeline-max-steps setting. */
    const val PIPELINE_MAX_STEPS_MAX: Int = 100

    /**
     * Default ceiling on the number of memory chunks scanned by the semantic
     * search path before ranking.
     */
    const val MEMORY_CHUNK_SEARCH_LIMIT_DEFAULT: Int = 1_000

    /**
     * Default Ollama-side context-window size (in tokens) assumed by
     * [ai.agent.android.data.local.ApiKeyManager] for any model whose
     * per-model override has not been configured.
     */
    const val OLLAMA_CONTEXT_WINDOW_DEFAULT: Int = 4_096

    /** Lower bound enforced when the user edits the memory-summary default limit. */
    const val MEMORY_SUMMARY_LIMIT_MIN: Int = 1

    /** Upper bound enforced when the user edits the memory-summary default limit. */
    const val MEMORY_SUMMARY_LIMIT_MAX: Int = 50
}
