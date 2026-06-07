package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.CloudProvider

/**
 * Domain-level abstraction over cloud-LLM model selection.
 *
 * Resolves the SDK-specific model object to use for a given provider, owning the full
 * fallback chain (user-configured id → provider default). Centralising this in the
 * data-layer impl (`KoogCloudLlmModelResolver`) keeps the executor free of both
 * `app.knotwork.android.data.*` imports and the per-provider `ApiKeyRepository.get*Model()`
 * branching.
 */
interface CloudLlmModelResolver {
    /**
     * Resolves the SDK model object to use for a given [provider].
     *
     * The implementation is responsible for reading the user-configured model id from
     * its own settings source (e.g. `ApiKeyRepository`) and substituting a sensible
     * provider-specific default when nothing is configured.
     *
     * @param provider The typed [CloudProvider] to resolve a model object for.
     * @return The SDK-specific model object (Koog `LLModel`), as [Any] for layering.
     */
    suspend fun resolveModel(provider: CloudProvider): Any
}
