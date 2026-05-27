package ai.agent.android.domain.pipelineio

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PipelinePresetImportOutcome
import ai.agent.android.domain.models.PresetCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PipelinePresetJsonSerializer].
 *
 * The host JVM provides a full `org.json` implementation via the
 * `org.json:json` testImplementation dependency, so no Robolectric is
 * needed.
 */
class PipelinePresetJsonSerializerTest {

    private val sampleGraph = PipelineGraph(
        id = "graph-uuid",
        name = "Embedded graph",
        updatedAt = 1_700_000_000_000L,
        nodes = listOf(
            NodeModel(
                id = "n1",
                type = NodeType.INPUT,
                x = 0f,
                y = 0f,
                label = "Start",
                systemPrompt = null,
                contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
            ),
            NodeModel(
                id = "n2",
                type = NodeType.LITE_RT,
                x = 100f,
                y = 100f,
                label = "Brain",
                systemPrompt = "You are an assistant.",
                contextConfig = NodeContextConfig.defaultForType(NodeType.LITE_RT),
            ),
            NodeModel(
                id = "n3",
                type = NodeType.OUTPUT,
                x = 200f,
                y = 100f,
                label = "Reply",
                contextConfig = NodeContextConfig.defaultForType(NodeType.OUTPUT),
            ),
        ),
        connections = listOf(
            ConnectionModel(id = "c1", sourceNodeId = "n1", targetNodeId = "n2"),
            ConnectionModel(id = "c2", sourceNodeId = "n2", targetNodeId = "n3"),
        ),
    )

    private val samplePreset = PipelinePreset(
        id = "local_only_qa",
        name = "Local-only Q&A",
        description = "INPUT → LITE_RT → OUTPUT, no network",
        category = PresetCategory.LOCAL,
        graph = sampleGraph,
        tags = listOf("offline", "qa"),
        isBundled = true,
    )

    @Test
    fun `given preset when serialize then emits preset-only fields plus pipeline schema`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("local_only_qa", json.getString("id"))
        assertEquals("Local-only Q&A", json.getString("name"))
        assertEquals("INPUT → LITE_RT → OUTPUT, no network", json.getString("description"))
        assertEquals("local", json.getString("category"))
        val tagsJson = json.getJSONArray("tags")
        assertEquals(2, tagsJson.length())
        assertEquals("offline", tagsJson.getString(0))
        assertEquals("qa", tagsJson.getString(1))
        // The embedded graph layout still flows through PipelineJsonSerializer.
        assertEquals(3, json.getJSONArray("nodes").length())
        assertEquals(2, json.getJSONArray("connections").length())
    }

    @Test
    fun `given preset round-tripped through serialize-parse then graph fields are preserved`() {
        val json = PipelinePresetJsonSerializer.serialize(samplePreset)

        val outcome = PipelinePresetJsonSerializer.parse(json, isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        val parsed = (outcome as PipelinePresetImportOutcome.Success).preset
        assertEquals("local_only_qa", parsed.id)
        assertEquals("Local-only Q&A", parsed.name)
        assertEquals(PresetCategory.LOCAL, parsed.category)
        assertEquals(listOf("offline", "qa"), parsed.tags)
        assertTrue(parsed.isBundled)
        assertEquals(3, parsed.graph.nodes.size)
        assertEquals(2, parsed.graph.connections.size)
        assertEquals("Brain", parsed.graph.nodes.first { it.id == "n2" }.label)
    }

    @Test
    fun `given isBundled false when parse then flag is preserved on the resulting preset`() {
        val json = PipelinePresetJsonSerializer.serialize(samplePreset.copy(isBundled = false))

        val outcome = PipelinePresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        val parsed = (outcome as PipelinePresetImportOutcome.Success).preset
        assertEquals(false, parsed.isBundled)
    }

    @Test
    fun `given malformed JSON when parse then returns Failure`() {
        val outcome = PipelinePresetJsonSerializer.parse("not json", isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Failure)
        val message = (outcome as PipelinePresetImportOutcome.Failure).message
        assertTrue("expected 'Invalid JSON' in message: $message", message.contains("Invalid JSON"))
    }

    @Test
    fun `given missing category when parse then category defaults to OTHER`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.remove("category")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        assertEquals(PresetCategory.OTHER, (outcome as PipelinePresetImportOutcome.Success).preset.category)
    }

    @Test
    fun `given unknown category when parse then category defaults to OTHER`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.put("category", "future_bucket")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        assertEquals(PresetCategory.OTHER, (outcome as PipelinePresetImportOutcome.Success).preset.category)
    }

    @Test
    fun `given missing description when parse then description is empty string`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.remove("description")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        assertEquals("", (outcome as PipelinePresetImportOutcome.Success).preset.description)
    }

    @Test
    fun `given missing tags when parse then tags are empty list`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.remove("tags")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        assertEquals(emptyList<String>(), (outcome as PipelinePresetImportOutcome.Success).preset.tags)
    }

    @Test
    fun `given schema version mismatch when parse then returns SchemaMismatch with the same preset fields`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.put("schemaVersion", 99)

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.SchemaMismatch)
        val mismatch = outcome as PipelinePresetImportOutcome.SchemaMismatch
        assertEquals(99, mismatch.foundVersion)
        assertEquals(1, mismatch.expectedVersion)
        assertEquals(PresetCategory.LOCAL, mismatch.preset.category)
        assertEquals(samplePreset.tags, mismatch.preset.tags)
    }

    @Test
    fun `given missing required graph field when parse then returns Failure`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        json.remove("nodes")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Failure)
    }

    @Test
    fun `given empty tag entries when parse then they are dropped`() {
        val json = JSONObject(PipelinePresetJsonSerializer.serialize(samplePreset))
        // Inject a blank tag — the parser must skip it rather than emit "".
        json.getJSONArray("tags").put("")

        val outcome = PipelinePresetJsonSerializer.parse(json.toString(), isBundled = true)

        assertTrue(outcome is PipelinePresetImportOutcome.Success)
        val parsed = (outcome as PipelinePresetImportOutcome.Success).preset
        assertEquals(listOf("offline", "qa"), parsed.tags)
        assertNotNull(parsed.graph)
    }
}
