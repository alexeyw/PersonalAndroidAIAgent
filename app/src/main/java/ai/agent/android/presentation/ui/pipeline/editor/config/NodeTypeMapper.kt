package ai.agent.android.presentation.ui.pipeline.editor.config

import ai.agent.android.domain.models.CloudProvider as DomainCloudProvider
import ai.agent.android.domain.models.NodeType as DomainNodeType
import app.knotwork.design.components.pipelineeditor.CloudProvider as CatalogCloudProvider
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

/**
 * Bridges between the production-domain [DomainNodeType] (`ai.agent.android.domain.models.NodeType`)
 * and the design-catalog [CatalogNodeType] (`app.knotwork.design.components.pipelineeditor.NodeType`).
 *
 * The catalog enum is intentionally independent of `:app` so the design module has zero coupling
 * to production code (`pipelineeditor/NodeType.kt`). The two enums are kept name-for-name aligned
 * — adding a new node type requires editing both — and this mapper is the single translation
 * point used by the editor screen.
 */
internal object NodeTypeMapper {

    /**
     * Maps a domain node type onto its catalog counterpart.
     *
     * @return the catalog enum entry with the same conceptual identity.
     */
    fun toCatalog(type: DomainNodeType): CatalogNodeType = when (type) {
        DomainNodeType.INPUT -> CatalogNodeType.INPUT
        DomainNodeType.OUTPUT -> CatalogNodeType.OUTPUT
        DomainNodeType.LITE_RT -> CatalogNodeType.LITE_RT
        DomainNodeType.CLOUD -> CatalogNodeType.CLOUD
        DomainNodeType.INTENT_ROUTER -> CatalogNodeType.INTENT_ROUTER
        DomainNodeType.IF_CONDITION -> CatalogNodeType.IF_CONDITION
        DomainNodeType.CLARIFICATION -> CatalogNodeType.CLARIFICATION
        DomainNodeType.TOOL -> CatalogNodeType.TOOL
        DomainNodeType.DECOMPOSITION -> CatalogNodeType.DECOMPOSITION
        DomainNodeType.QUEUE_PROCESSOR -> CatalogNodeType.QUEUE_PROCESSOR
        DomainNodeType.EVALUATION -> CatalogNodeType.EVALUATION
        DomainNodeType.SUMMARY -> CatalogNodeType.SUMMARY
    }

    /**
     * Maps a catalog node type back onto its production-domain counterpart.
     *
     * @return the domain enum entry with the same conceptual identity.
     */
    fun toDomain(type: CatalogNodeType): DomainNodeType = when (type) {
        CatalogNodeType.INPUT -> DomainNodeType.INPUT
        CatalogNodeType.OUTPUT -> DomainNodeType.OUTPUT
        CatalogNodeType.LITE_RT -> DomainNodeType.LITE_RT
        CatalogNodeType.CLOUD -> DomainNodeType.CLOUD
        CatalogNodeType.INTENT_ROUTER -> DomainNodeType.INTENT_ROUTER
        CatalogNodeType.IF_CONDITION -> DomainNodeType.IF_CONDITION
        CatalogNodeType.CLARIFICATION -> DomainNodeType.CLARIFICATION
        CatalogNodeType.TOOL -> DomainNodeType.TOOL
        CatalogNodeType.DECOMPOSITION -> DomainNodeType.DECOMPOSITION
        CatalogNodeType.QUEUE_PROCESSOR -> DomainNodeType.QUEUE_PROCESSOR
        CatalogNodeType.EVALUATION -> DomainNodeType.EVALUATION
        CatalogNodeType.SUMMARY -> DomainNodeType.SUMMARY
    }
}

/**
 * Bridges between the catalog-side [CatalogCloudProvider] enum (used by `CloudConfig` forms)
 * and the production-domain [DomainCloudProvider] (persisted as `cloudProvider` wire-id).
 *
 * The catalog set is intentionally narrower (`OPEN_AI / ANTHROPIC / GOOGLE / COMPATIBLE`)
 * because the design language only enumerates user-presentable provider tiles. The domain
 * adds `DEEPSEEK` and `OLLAMA`, which both map onto catalog's `COMPATIBLE` slot — they
 * use the OpenAI-compatible wire protocol.
 */
internal object CloudProviderMapper {

    /**
     * Translates the catalog form's selection into the domain wire-id.
     *
     * @return the matching [DomainCloudProvider]; `COMPATIBLE` maps to [DomainCloudProvider.DEEPSEEK]
     * as the canonical "OpenAI-compatible" instance because it is the only such provider with a
     * dedicated wire-id today.
     */
    fun toDomain(provider: CatalogCloudProvider): DomainCloudProvider = when (provider) {
        CatalogCloudProvider.OPEN_AI -> DomainCloudProvider.OPENAI
        CatalogCloudProvider.ANTHROPIC -> DomainCloudProvider.ANTHROPIC
        CatalogCloudProvider.GOOGLE -> DomainCloudProvider.GOOGLE
        CatalogCloudProvider.COMPATIBLE -> DomainCloudProvider.DEEPSEEK
    }

    /**
     * Translates a domain wire-id into the matching catalog selection. Both [DomainCloudProvider.DEEPSEEK]
     * and [DomainCloudProvider.OLLAMA] map to the catalog's `COMPATIBLE` tile.
     *
     * @return the catalog enum entry the form should pre-select; defaults to [CatalogCloudProvider.OPEN_AI]
     * when [provider] is `null` (legacy CLOUD nodes saved before Phase 21 with no explicit provider).
     */
    fun toCatalog(provider: DomainCloudProvider?): CatalogCloudProvider = when (provider) {
        DomainCloudProvider.OPENAI -> CatalogCloudProvider.OPEN_AI
        DomainCloudProvider.ANTHROPIC -> CatalogCloudProvider.ANTHROPIC
        DomainCloudProvider.GOOGLE -> CatalogCloudProvider.GOOGLE
        DomainCloudProvider.DEEPSEEK,
        DomainCloudProvider.OLLAMA,
        -> CatalogCloudProvider.COMPATIBLE
        null -> CatalogCloudProvider.OPEN_AI
    }
}
