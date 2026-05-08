package ai.agent.android.domain.engine

/**
 * Domain-level abstraction over cloud-LLM model selection.
 *
 * Resolves a provider's model identifier (the user-configured one when present, otherwise
 * the provider's default) into the concrete model object expected by the underlying SDK.
 * The implementation lives in the data layer (`KoogCloudLlmModelResolver`) and returns
 * a Koog `LLModel` cast to [Any]; consumers downcast at the call site.
 *
 * Decoupling the executor from `KoogModelMapper` keeps the domain layer free of
 * `ai.agent.android.data.*` imports while preserving the same selection semantics.
 */
interface CloudLlmModelResolver {
    /**
     * Resolves the SDK model object to use for a given [provider].
     *
     * @param provider Lowercase provider key — `"openai"`, `"anthropic"`, `"google"`,
     *                 `"deepseek"`, `"ollama"`.
     * @param configuredModelId The model id selected by the user (`null` falls back to the
     *                          provider's default).
     * @return The SDK-specific model object (Koog `LLModel`), as [Any] for layering.
     */
    suspend fun resolveModel(provider: String, configuredModelId: String?): Any
}
