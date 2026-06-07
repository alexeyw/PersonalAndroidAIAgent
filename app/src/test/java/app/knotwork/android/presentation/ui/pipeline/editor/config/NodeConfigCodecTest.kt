package app.knotwork.android.presentation.ui.pipeline.editor.config

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.design.components.pipelineeditor.ClarificationConfig
import app.knotwork.design.components.pipelineeditor.CloudConfig
import app.knotwork.design.components.pipelineeditor.CloudProvider
import app.knotwork.design.components.pipelineeditor.IfConditionConfig
import app.knotwork.design.components.pipelineeditor.LiteRtConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

class NodeConfigCodecTest {

    // Timber call sites in the codec are no-ops without a planted tree, so no setup is needed.

    private fun node(type: NodeType, label: String = "Node"): NodeModel = NodeModel(
        id = "n-1",
        type = type,
        x = 0f,
        y = 0f,
        label = label,
    )

    @Test
    fun `given LiteRt config when encode-then-decode then payload preserved`() {
        val source = node(NodeType.LITE_RT, "Local")
        val config = LiteRtConfig(
            title = "Local",
            modelId = "gemma-2b-it",
            systemPrompt = "Answer concisely as of \$DATE.",
            temperature = 0.5f,
            topP = 0.85f,
            maxNewTokens = 1024,
            stopTokens = listOf("###", "END"),
        )
        val json = NodeConfigCodec.encode(config)
        val applied = source.copy(configJson = json)
        val decoded = NodeConfigCodec.decode(applied) as LiteRtConfig
        assertEquals("Local", decoded.title)
        assertEquals("gemma-2b-it", decoded.modelId)
        assertEquals(0.5f, decoded.temperature, 1e-3f)
        assertEquals(0.85f, decoded.topP, 1e-3f)
        assertEquals(1024, decoded.maxNewTokens)
        assertEquals(listOf("###", "END"), decoded.stopTokens)
    }

    @Test
    fun `given Cloud config when encode-then-decode then provider preserved`() {
        val source = node(NodeType.CLOUD, "Cloud")
        val config = CloudConfig(
            title = "Cloud",
            provider = app.knotwork.design.components.pipelineeditor.CloudProvider.ANTHROPIC,
            model = "claude-opus-4-7",
            systemPrompt = "You are helpful.",
            temperature = 0.3f,
            maxTokens = 2048,
            timeoutMs = 45_000,
        )
        val applied = source.copy(configJson = NodeConfigCodec.encode(config))
        val decoded = NodeConfigCodec.decode(applied) as CloudConfig
        assertEquals(app.knotwork.design.components.pipelineeditor.CloudProvider.ANTHROPIC, decoded.provider)
        assertEquals("claude-opus-4-7", decoded.model)
        assertEquals(2048, decoded.maxTokens)
        assertEquals(45_000, decoded.timeoutMs)
    }

    @Test
    fun `given Cloud AUTO provider when apply then flat field is the auto sentinel and decodes back to AUTO`() {
        val source = node(NodeType.CLOUD, "Cloud")
        val config = CloudConfig(title = "Cloud", provider = CloudProvider.AUTO)

        val applied = NodeConfigCodec.apply(source, config)

        // Saving the sheet must persist "auto" (not a concrete provider), so the
        // runtime keeps auto-routing.
        assertEquals("auto", applied.cloudProvider)
        // …and re-decoding the saved node round-trips back to AUTO.
        assertEquals(CloudProvider.AUTO, (NodeConfigCodec.decode(applied) as CloudConfig).provider)
    }

    @Test
    fun `given legacy auto CLOUD node with no configJson when decode then provider is AUTO`() {
        // Regression for the round-trip bug: a browser-edited / default CLOUD
        // node persists cloudProvider="auto" with no rich payload. It must decode
        // as AUTO, not silently fall back to OpenAI.
        val legacy = node(NodeType.CLOUD, "Cloud").copy(cloudProvider = "auto", configJson = null)

        val decoded = NodeConfigCodec.decode(legacy) as CloudConfig

        assertEquals(CloudProvider.AUTO, decoded.provider)
    }

    @Test
    fun `given IfCondition config when encode-then-decode then expression preserved`() {
        val src = node(NodeType.IF_CONDITION, "Branch")
        val config = IfConditionConfig(
            title = "Branch",
            expression = "score > 0.8",
            labelTrue = "Yes",
            labelFalse = "No",
        )
        val decoded = NodeConfigCodec.decode(src.copy(configJson = NodeConfigCodec.encode(config))) as IfConditionConfig
        assertEquals("score > 0.8", decoded.expression)
        assertEquals("Yes", decoded.labelTrue)
        assertEquals("No", decoded.labelFalse)
    }

    @Test
    fun `given Clarification config with timeout when round-trip then timeout preserved`() {
        val src = node(NodeType.CLARIFICATION, "Ask")
        val config = ClarificationConfig(
            title = "Ask",
            questionTemplate = "What would you like to do?",
            quickReplies = listOf("Continue", "Skip"),
            timeoutMs = 30_000,
        )
        val decoded = NodeConfigCodec.decode(
            src.copy(configJson = NodeConfigCodec.encode(config)),
        ) as ClarificationConfig
        assertEquals("What would you like to do?", decoded.questionTemplate)
        assertEquals(listOf("Continue", "Skip"), decoded.quickReplies)
        assertEquals(30_000, decoded.timeoutMs)
    }

    @Test
    fun `given pre-Phase21 node when decode then derived from legacy fields`() {
        val src = NodeModel(
            id = "n",
            type = NodeType.LITE_RT,
            x = 0f,
            y = 0f,
            label = "Local",
            modelPath = "gemma-2b-it",
            systemPrompt = "Hi",
        )
        val decoded = NodeConfigCodec.decode(src) as LiteRtConfig
        assertEquals("Local", decoded.title)
        assertEquals("gemma-2b-it", decoded.modelId)
        assertEquals("Hi", decoded.systemPrompt)
    }

    @Test
    fun `given malformed JSON when decode then falls back to legacy derivation`() {
        val src = NodeModel(
            id = "n",
            type = NodeType.LITE_RT,
            x = 0f,
            y = 0f,
            label = "Local",
            systemPrompt = "fallback",
            configJson = "not-json{",
        )
        val decoded = NodeConfigCodec.decode(src) as LiteRtConfig
        assertEquals("fallback", decoded.systemPrompt)
    }

    @Test
    fun `given every node type when defaultFor then yields the matching NodeConfig`() {
        CatalogNodeType.entries.forEach { type ->
            val config = NodeConfigCodec.defaultFor(type, title = "T")
            assertEquals(type, config.type)
            assertEquals("T", config.title)
        }
    }

    @Test
    fun `given config when apply then label and configJson are refreshed on the NodeModel`() {
        val src = node(NodeType.LITE_RT, "Old")
        val edited = LiteRtConfig(title = "New", modelId = "m", systemPrompt = "sp")
        val patched = NodeConfigCodec.apply(src, edited)
        assertEquals("New", patched.label)
        assertNotNull(patched.configJson)
        assertTrue(patched.configJson!!.contains("\"title\":\"New\""))
        assertEquals("sp", patched.systemPrompt)
    }
}
