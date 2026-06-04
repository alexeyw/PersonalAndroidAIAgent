package ai.agent.android.presentation.ui.pipeline.editor.config

import org.junit.Assert.assertEquals
import org.junit.Test
import ai.agent.android.domain.models.CloudProvider as DomainCloudProvider
import ai.agent.android.domain.models.NodeType as DomainNodeType
import app.knotwork.design.components.pipelineeditor.CloudProvider as CatalogCloudProvider
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

class NodeTypeMapperTest {

    @Test
    fun `given every domain node type when toCatalog round-trip then identity`() {
        DomainNodeType.entries.forEach { type ->
            val catalog = NodeTypeMapper.toCatalog(type)
            val back = NodeTypeMapper.toDomain(catalog)
            assertEquals(type, back)
        }
    }

    @Test
    fun `given every catalog node type when toDomain round-trip then identity`() {
        CatalogNodeType.entries.forEach { type ->
            val domain = NodeTypeMapper.toDomain(type)
            val back = NodeTypeMapper.toCatalog(domain)
            assertEquals(type, back)
        }
    }

    @Test
    fun `given catalog COMPATIBLE when toDomain then maps to DEEPSEEK`() {
        assertEquals(DomainCloudProvider.DEEPSEEK, CloudProviderMapper.toDomain(CatalogCloudProvider.COMPATIBLE))
    }

    @Test
    fun `given domain DEEPSEEK or OLLAMA when toCatalog then maps to COMPATIBLE`() {
        assertEquals(CatalogCloudProvider.COMPATIBLE, CloudProviderMapper.toCatalog(DomainCloudProvider.DEEPSEEK))
        assertEquals(CatalogCloudProvider.COMPATIBLE, CloudProviderMapper.toCatalog(DomainCloudProvider.OLLAMA))
    }

    @Test
    fun `given null provider when toCatalog then defaults to OPEN_AI`() {
        assertEquals(CatalogCloudProvider.OPEN_AI, CloudProviderMapper.toCatalog(null))
    }

    @Test
    fun `given primary cloud providers when round-trip then identity`() {
        listOf(
            CatalogCloudProvider.OPEN_AI,
            CatalogCloudProvider.ANTHROPIC,
            CatalogCloudProvider.GOOGLE,
        ).forEach { c ->
            val domain = CloudProviderMapper.toDomain(c)
            assertEquals(c, CloudProviderMapper.toCatalog(domain))
        }
    }

    @Test
    fun `given catalog AUTO when toDomain then null`() {
        assertEquals(null, CloudProviderMapper.toDomain(CatalogCloudProvider.AUTO))
    }

    @Test
    fun `given catalog AUTO when toWireId then auto sentinel`() {
        assertEquals(DomainCloudProvider.AUTO_KEY, CloudProviderMapper.toWireId(CatalogCloudProvider.AUTO))
        assertEquals("auto", CloudProviderMapper.toWireId(CatalogCloudProvider.AUTO))
    }

    @Test
    fun `given concrete catalog providers when toWireId then concrete wire id`() {
        assertEquals("openai", CloudProviderMapper.toWireId(CatalogCloudProvider.OPEN_AI))
        assertEquals("deepseek", CloudProviderMapper.toWireId(CatalogCloudProvider.COMPATIBLE))
    }

    @Test
    fun `given auto wire id when fromWireId then AUTO preserved`() {
        assertEquals(CatalogCloudProvider.AUTO, CloudProviderMapper.fromWireId("auto"))
        assertEquals(CatalogCloudProvider.AUTO, CloudProviderMapper.fromWireId("AUTO"))
    }

    @Test
    fun `given null or concrete wire id when fromWireId then mapped tile`() {
        // null = legacy "no provider" → OpenAI default (distinct from auto).
        assertEquals(CatalogCloudProvider.OPEN_AI, CloudProviderMapper.fromWireId(null))
        assertEquals(CatalogCloudProvider.ANTHROPIC, CloudProviderMapper.fromWireId("anthropic"))
        assertEquals(CatalogCloudProvider.COMPATIBLE, CloudProviderMapper.fromWireId("ollama"))
    }

    @Test
    fun `given every catalog provider when wire-id round-trip then identity`() {
        CatalogCloudProvider.entries.forEach { provider ->
            assertEquals(provider, CloudProviderMapper.fromWireId(CloudProviderMapper.toWireId(provider)))
        }
    }
}
