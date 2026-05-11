package ai.agent.android.domain.engine

import ai.agent.android.domain.models.CloudProvider

/**
 * Domain-level abstraction over the cloud LLM client construction.
 *
 * The implementation lives in the data layer (`KoogClientFactory`) and bridges to whichever
 * third-party SDK is actually used (currently Koog). Domain-side consumers — most notably
 * `CloudLlmNodeExecutor` — depend only on this interface so they remain free of
 * `ai.agent.android.data.*` imports and obey the Clean Architecture dependency rule
 * (`data → domain ← presentation`).
 *
 * The returned client is typed as [Any] because the concrete shape (`ai.koog…LLMClient`)
 * is supplied by an external library; consumers cast at the call site to invoke the
 * library API. This keeps the boundary one-directional without smuggling project-internal
 * data-layer types into domain.
 */
interface CloudLlmClientFactory {
    /**
     * Creates a streaming LLM client for the given [provider].
     *
     * @param provider The typed [CloudProvider] to construct a client for.
     * @return The constructed client, or `null` when the user has not configured the
     *         credentials/base URL required by [provider].
     */
    suspend fun createClient(provider: CloudProvider): Any?
}
