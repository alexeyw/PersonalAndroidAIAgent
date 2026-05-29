package ai.agent.android.domain.constants

import ai.agent.android.domain.services.EmbeddingProvider

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
     * Default top-K for long-term memory retrieval: how many ranked chunks a
     * single search returns into a node's context block. Distinct from
     * [MEMORY_CHUNK_SEARCH_LIMIT_DEFAULT], which caps the *scanned* pool.
     */
    const val MEMORY_SEARCH_TOP_K_DEFAULT: Int = 5

    /**
     * Default minimum cosine-similarity score a memory chunk must reach to be
     * surfaced during retrieval. Chunks below this are filtered out before
     * reaching the prompt.
     */
    const val MEMORY_SEARCH_THRESHOLD_DEFAULT: Float = 0.55f

    /**
     * Default recency half-life, in days, used by the memory re-ranker: a
     * non-pinned chunk this old keeps half of its raw cosine similarity. Lower
     * values bias retrieval harder towards fresh facts.
     */
    const val MEMORY_RECENCY_HALF_LIFE_DAYS_DEFAULT: Int = 30

    /** Lower bound enforced when the user edits the recency half-life. */
    const val MEMORY_RECENCY_HALF_LIFE_DAYS_MIN: Int = 7

    /** Upper bound enforced when the user edits the recency half-life. */
    const val MEMORY_RECENCY_HALF_LIFE_DAYS_MAX: Int = 180

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

    /**
     * Default repetition-penalty applied to local LLM generation. `1.0f` is the
     * neutral identity. The Settings slider exposes the documented `1.0..2.0`
     * range so the user can dial up an anti-repetition bias without ever
     * crossing into the divergence band above `2.0`.
     */
    const val REPETITION_PENALTY_DEFAULT: Float = 1.1f

    /** Lower bound enforced when the user edits the repetition-penalty slider. */
    const val REPETITION_PENALTY_MIN: Float = 1.0f

    /** Upper bound enforced when the user edits the repetition-penalty slider. */
    const val REPETITION_PENALTY_MAX: Float = 2.0f

    /**
     * Default fraction of the memory context budget at which automatic
     * summarization kicks in. Range `0f..1f`. `0.8f` matches the mockup
     * (80 %). Lower values trigger summarisation sooner at the cost of more
     * embedding work; higher values keep raw chunks around longer.
     */
    const val AUTO_SUMMARIZE_THRESHOLD_DEFAULT: Float = 0.8f

    /**
     * Maximum length (in characters) of the user-editable system instructions
     * block. Mirrors the `218 / 4 000 chars` counter shown in the System
     * instructions card. The bound exists so a runaway paste cannot inflate
     * the prompt past what an on-device model can fit in context.
     */
    const val SYSTEM_INSTRUCTIONS_CHAR_LIMIT: Int = 4_000

    /**
     * Default active embedding-provider id for the long-term memory subsystem.
     *
     * Mirrors [EmbeddingProvider.ID_USE] (the on-device Universal Sentence
     * Encoder) — referenced rather than re-typed so the default and the
     * provider's own id can never drift apart.
     */
    const val ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT: String = EmbeddingProvider.ID_USE

    /**
     * Default for the "Auto-extract from conversations" memory toggle. `true`
     * so the long-term memory fills itself out of the box: after a pipeline run
     * completes, [ai.agent.android.domain.usecases.MemoryExtractionUseCase]
     * distils durable facts from the dialogue. Users who prefer to curate
     * memory by hand can turn it off in Settings → Memory.
     */
    const val AUTO_EXTRACT_ENABLED_DEFAULT: Boolean = true

    /**
     * Default for the background memory-compaction toggle. `true` so the
     * long-term memory keeps itself tidy out of the box: a daily worker
     * (`MemoryCompactionWorker`, charging + idle only) clusters old non-pinned
     * chunks and consolidates each dense cluster into a single summary chunk.
     * Users who prefer their raw facts untouched can turn it off in
     * Settings → Memory (UI lands in Phase 25 / Task 9).
     */
    const val MEMORY_COMPACTION_ENABLED_DEFAULT: Boolean = true

    /**
     * Default age, in days, after which a non-pinned chunk becomes a candidate
     * for compaction. Fresh chunks are left alone so recently-learned facts
     * keep their exact wording; only stale ones are eligible for clustering.
     */
    const val MEMORY_COMPACTION_AGE_DAYS_DEFAULT: Int = 30

    /** Lower bound enforced when the user edits the compaction age window. */
    const val MEMORY_COMPACTION_AGE_DAYS_MIN: Int = 7

    /** Upper bound enforced when the user edits the compaction age window. */
    const val MEMORY_COMPACTION_AGE_DAYS_MAX: Int = 90

    /**
     * Hard ceiling on the total number of stored memory chunks. When the table
     * grows past this, compaction is triggered out-of-schedule (without waiting
     * for the daily charging-and-idle window) to keep the database bounded.
     */
    const val MAX_MEMORY_CHUNKS_DEFAULT: Int = 5_000

    /** Lower bound enforced when the user edits the max-chunks hard limit. */
    const val MAX_MEMORY_CHUNKS_MIN: Int = 1_000

    /** Upper bound enforced when the user edits the max-chunks hard limit. */
    const val MAX_MEMORY_CHUNKS_MAX: Int = 20_000
}
