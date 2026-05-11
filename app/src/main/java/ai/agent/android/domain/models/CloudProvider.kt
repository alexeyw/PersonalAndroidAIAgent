package ai.agent.android.domain.models

/**
 * Type-safe identifier for the cloud LLM providers the agent can route to.
 *
 * Each enum constant carries the lowercase wire-id that is persisted in the
 * database (`pipeline_nodes.cloudProvider`), serialized in the pipeline JSON
 * (`PipelineJsonSerializer`), and exposed to the LLM as a tool-argument
 * (`DelegateTaskTool.executeDelegation`). Centralising the mapping here lets
 * every consumer perform exhaustive `when`-dispatch and removes the previous
 * cluster of `when (s: String)` blocks scattered across the data and domain
 * layers.
 *
 * The companion object owns the parsing rules — including the historical
 * `"gemini"` alias for [GOOGLE] — so that incoming wire data only has to be
 * decoded once on the boundary.
 */
enum class CloudProvider(
    /** Lowercase wire-id persisted to disk and accepted by the cloud LLM router. */
    val id: String,
) {
    /** OpenAI (GPT family). */
    OPENAI("openai"),

    /** Anthropic (Claude family). */
    ANTHROPIC("anthropic"),

    /** Google AI Studio / Gemini family. */
    GOOGLE("google"),

    /** DeepSeek hosted API. */
    DEEPSEEK("deepseek"),

    /** Self-hosted Ollama instance (typically over Wi-Fi). */
    OLLAMA("ollama"),
    ;

    /** Owns the wire-id ↔ enum parsing rules (including the legacy `"gemini"` alias). */
    companion object {
        /**
         * UI marker that means "let the executor pick a provider based on the
         * configured API keys". This is **not** a real provider — it never
         * survives [fromId] and must be filtered out before dispatching.
         */
        const val AUTO_KEY: String = "auto"

        /**
         * Parses a wire/UI provider id into a typed [CloudProvider].
         *
         * Matching is case-insensitive. The legacy alias `"gemini"` is mapped
         * to [GOOGLE] because earlier versions of the project persisted that
         * label for Google models. Unknown ids, [AUTO_KEY], and `null` all
         * return `null` — callers decide whether the absence is an error
         * (validation) or a fallback trigger (auto-detect).
         *
         * @param id Provider id as stored on disk or chosen by the user.
         * @return The matching [CloudProvider], or `null` when the id is
         *         unknown / blank / `null` / [AUTO_KEY].
         */
        fun fromId(id: String?): CloudProvider? = when (id?.lowercase()) {
            null -> null
            "openai" -> OPENAI
            "anthropic" -> ANTHROPIC
            "google", "gemini" -> GOOGLE
            "deepseek" -> DEEPSEEK
            "ollama" -> OLLAMA
            else -> null
        }
    }
}
