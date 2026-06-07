package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PipelineJsonSerializer].
 *
 * The host JVM provides a full `org.json` implementation via the
 * `org.json:json` testImplementation dependency, so no Robolectric is
 * needed — the serializer is pure Kotlin + org.json with no framework
 * touch points.
 */
class PipelineJsonSerializerTest {

    private val sampleGraph = PipelineGraph(
        id = "pipeline-uuid",
        name = "Demo pipeline",
        updatedAt = 1_700_000_000_000L,
        nodes = listOf(
            NodeModel(
                id = "node-1",
                type = NodeType.INPUT,
                x = 10f,
                y = 20f,
                label = "Start",
                systemPrompt = null,
                contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
            ),
            NodeModel(
                id = "node-2",
                type = NodeType.CLOUD,
                x = 200f,
                y = 80f,
                label = "Brain",
                systemPrompt = "You are an agent.",
                cloudProvider = "anthropic",
                contextConfig = NodeContextConfig(
                    chatHistory = true,
                    originalTask = true,
                    nodeInput = true,
                    longTermMemory = false,
                    toolResults = false,
                ),
            ),
            NodeModel(
                id = "node-3",
                type = NodeType.IF_CONDITION,
                x = 400f,
                y = 80f,
                label = "Branch",
                conditionKeywords = "urgent, important",
                conditionPrompt = "Classify urgency",
                conditionComplexity = 5,
                contextConfig = NodeContextConfig.defaultForType(NodeType.IF_CONDITION),
            ),
            NodeModel(
                id = "node-4",
                type = NodeType.TOOL,
                x = 600f,
                y = 80f,
                label = "Search",
                toolName = "web_search",
                contextConfig = NodeContextConfig.defaultForType(NodeType.TOOL),
            ),
            NodeModel(
                id = "node-5",
                type = NodeType.OUTPUT,
                x = 800f,
                y = 80f,
                label = "Reply",
                systemPrompt = "Compose final answer.",
                contextConfig = NodeContextConfig.ALL_ENABLED,
            ),
            NodeModel(
                id = "node-6",
                type = NodeType.CLARIFICATION,
                x = 200f,
                y = 200f,
                label = "Ask",
                clarificationTimeoutMs = 30_000L,
                contextConfig = NodeContextConfig.defaultForType(NodeType.CLARIFICATION),
            ),
        ),
        connections = listOf(
            ConnectionModel(id = "c1", sourceNodeId = "node-1", targetNodeId = "node-2", label = null),
            ConnectionModel(id = "c2", sourceNodeId = "node-2", targetNodeId = "node-3", label = null),
            ConnectionModel(id = "c3", sourceNodeId = "node-3", targetNodeId = "node-4", label = "True"),
            ConnectionModel(id = "c4", sourceNodeId = "node-3", targetNodeId = "node-5", label = "False"),
            ConnectionModel(id = "c5", sourceNodeId = "node-4", targetNodeId = "node-5", label = null),
        ),
    )

    @Test
    fun `serialize then parse round-trips a full graph`() {
        val json = PipelineJsonSerializer.serialize(sampleGraph)

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Success)
        val parsed = (outcome as PipelineImportOutcome.Success).graph
        assertEquals(sampleGraph.id, parsed.id)
        assertEquals(sampleGraph.name, parsed.name)
        assertEquals(sampleGraph.updatedAt, parsed.updatedAt)
        assertEquals(sampleGraph.nodes.size, parsed.nodes.size)
        assertEquals(sampleGraph.connections.size, parsed.connections.size)

        // Field-level checks for representative node types
        val cloud = parsed.nodes.first { it.type == NodeType.CLOUD }
        assertEquals("Brain", cloud.label)
        assertEquals("anthropic", cloud.cloudProvider)
        assertEquals("You are an agent.", cloud.systemPrompt)
        assertEquals(false, cloud.contextConfig.longTermMemory)

        val ifNode = parsed.nodes.first { it.type == NodeType.IF_CONDITION }
        assertEquals("urgent, important", ifNode.conditionKeywords)
        assertEquals(5, ifNode.conditionComplexity)

        val tool = parsed.nodes.first { it.type == NodeType.TOOL }
        assertEquals("web_search", tool.toolName)

        val clarification = parsed.nodes.first { it.type == NodeType.CLARIFICATION }
        assertEquals(30_000L, clarification.clarificationTimeoutMs)

        // Connection labels survive
        val branchTrue = parsed.connections.first { it.id == "c3" }
        assertEquals("True", branchTrue.label)
        val branchFalse = parsed.connections.first { it.id == "c4" }
        assertEquals("False", branchFalse.label)
        // Empty labels round-trip as null (not "")
        assertNull(parsed.connections.first { it.id == "c1" }.label)
    }

    @Test
    fun `parse reports SchemaMismatch when schemaVersion differs`() {
        val json = """
            {
              "schemaVersion": 99,
              "id": "p",
              "name": "Future",
              "updatedAt": 0,
              "nodes": [
                {"id":"n1","type":"INPUT","position":{"x":0,"y":0},"label":"In",
                 "config":{},"contextConfig":{}}
              ],
              "connections": []
            }
        """.trimIndent()

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue("Expected SchemaMismatch but was $outcome", outcome is PipelineImportOutcome.SchemaMismatch)
        val mismatch = outcome as PipelineImportOutcome.SchemaMismatch
        assertEquals(99, mismatch.foundVersion)
        assertEquals(PipelineJsonSerializer.CURRENT_SCHEMA_VERSION, mismatch.expectedVersion)
        // Best-effort graph still produced
        assertEquals(1, mismatch.graph.nodes.size)
        assertEquals(NodeType.INPUT, mismatch.graph.nodes.single().type)
    }

    @Test
    fun `parse rejects document missing schemaVersion`() {
        val json = """{ "id":"p","name":"x","nodes":[],"connections":[] }"""

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Failure)
        val msg = (outcome as PipelineImportOutcome.Failure).message
        assertTrue("Message should mention schemaVersion: $msg", msg.contains("schemaVersion"))
    }

    @Test
    fun `parse rejects malformed JSON`() {
        val outcome = PipelineJsonSerializer.parse("{ not json")
        assertTrue(outcome is PipelineImportOutcome.Failure)
    }

    @Test
    fun `parse rejects unknown node type`() {
        val json = """
            {
              "schemaVersion": 1, "id":"p", "name":"x",
              "nodes":[{"id":"n1","type":"BOGUS","position":{"x":0,"y":0},
                       "label":"x","config":{},"contextConfig":{}}],
              "connections":[]
            }
        """.trimIndent()

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Failure)
        assertTrue(
            "Failure should mention BOGUS",
            (outcome as PipelineImportOutcome.Failure).message.contains("BOGUS"),
        )
    }

    @Test
    fun `parse rejects connection referencing unknown node`() {
        val json = """
            {
              "schemaVersion": 1, "id":"p", "name":"x",
              "nodes":[{"id":"n1","type":"INPUT","position":{"x":0,"y":0},
                       "label":"x","config":{},"contextConfig":{}}],
              "connections":[{"id":"c1","fromNodeId":"n1","toNodeId":"ghost","label":null}]
            }
        """.trimIndent()

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Failure)
        assertTrue((outcome as PipelineImportOutcome.Failure).message.contains("ghost"))
    }

    @Test
    fun `parse falls back to per-type defaults when contextConfig is missing`() {
        val json = """
            {
              "schemaVersion": 1, "id":"p", "name":"x", "updatedAt": 0,
              "nodes":[{"id":"n1","type":"OUTPUT","position":{"x":0,"y":0},
                       "label":"Out","config":{}}],
              "connections":[]
            }
        """.trimIndent()

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Success)
        val node = (outcome as PipelineImportOutcome.Success).graph.nodes.single()
        // OUTPUT default is ALL_ENABLED
        assertEquals(NodeContextConfig.ALL_ENABLED, node.contextConfig)
    }

    @Test
    fun `serialize emits null for missing optional fields`() {
        val minimal = PipelineGraph(
            id = "p",
            name = "x",
            nodes = listOf(
                NodeModel(
                    id = "n1",
                    type = NodeType.INPUT,
                    x = 0f,
                    y = 0f,
                    systemPrompt = null,
                ),
            ),
            connections = emptyList(),
        )
        val json = PipelineJsonSerializer.serialize(minimal)
        // Spot-check that JSON null is used for absent strings rather than ""
        // — we look at the raw text instead of re-parsing, since re-parse is
        // covered by the round-trip test.
        assertNotNull(json)
        assertTrue(json.contains("\"systemPrompt\":null"))
        assertTrue(json.contains("\"toolName\":null"))
    }

    @Test
    fun `serialize then parse round-trips the rich nodeConfig payload`() {
        val payload = """
            {"v":1,"type":"CLOUD","title":"Brain","provider":"ANTHROPIC",
             "model":"claude","systemPrompt":"You are an agent.",
             "temperature":0.7,"maxTokens":1024,"timeoutMs":30000}
        """.trimIndent()
        val graph = graphWithCloudConfigJson(payload)

        val outcome = PipelineJsonSerializer.parse(PipelineJsonSerializer.serialize(graph))

        assertTrue(outcome is PipelineImportOutcome.Success)
        val cloud = (outcome as PipelineImportOutcome.Success).graph.nodes.first { it.type == NodeType.CLOUD }
        // The opaque blob round-trips: the rich fields survive verbatim even
        // though the domain serializer never interprets them.
        assertNotNull(cloud.configJson)
        val parsed = JSONObject(cloud.configJson!!)
        assertEquals(1, parsed.getInt("v"))
        assertEquals("ANTHROPIC", parsed.getString("provider"))
        assertEquals(0.7, parsed.getDouble("temperature"), 0.0001)
        assertEquals(1024, parsed.getInt("maxTokens"))
        // The flat config remains authoritative for the runtime.
        assertEquals("anthropic", cloud.cloudProvider)
    }

    @Test
    fun `serialize omits nodeConfig when configJson is absent`() {
        val json = PipelineJsonSerializer.serialize(graphWithCloudConfigJson(null))

        assertFalse(json.contains("\"nodeConfig\""))
    }

    @Test
    fun `serialize drops malformed configJson instead of emitting nodeConfig`() {
        val json = PipelineJsonSerializer.serialize(graphWithCloudConfigJson("{ not json"))

        assertFalse("Malformed configJson must not leak into the document", json.contains("\"nodeConfig\""))
        val outcome = PipelineJsonSerializer.parse(json)
        assertTrue(outcome is PipelineImportOutcome.Success)
        val cloud = (outcome as PipelineImportOutcome.Success).graph.nodes.first { it.type == NodeType.CLOUD }
        assertNull(cloud.configJson)
    }

    @Test
    fun `parse tolerates a non-object nodeConfig as null`() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "p",
              "name": "x",
              "nodes": [
                { "id": "n1", "type": "INPUT", "position": { "x": 0, "y": 0 }, "label": "In",
                  "nodeConfig": "oops-not-an-object" }
              ],
              "connections": []
            }
        """.trimIndent()

        val outcome = PipelineJsonSerializer.parse(json)

        assertTrue(outcome is PipelineImportOutcome.Success)
        assertNull((outcome as PipelineImportOutcome.Success).graph.nodes.single().configJson)
    }

    /**
     * Builds a minimal INPUT → CLOUD graph whose CLOUD node carries the given
     * [configJson] blob, used to exercise the opaque `nodeConfig` passthrough.
     */
    private fun graphWithCloudConfigJson(configJson: String?): PipelineGraph = PipelineGraph(
        id = "p",
        name = "x",
        nodes = listOf(
            NodeModel(
                id = "n1",
                type = NodeType.INPUT,
                x = 0f,
                y = 0f,
                systemPrompt = null,
                contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
            ),
            NodeModel(
                id = "n2",
                type = NodeType.CLOUD,
                x = 1f,
                y = 0f,
                systemPrompt = "You are an agent.",
                cloudProvider = "anthropic",
                contextConfig = NodeContextConfig.defaultForType(NodeType.CLOUD),
                configJson = configJson,
            ),
        ),
        connections = listOf(
            ConnectionModel(id = "c1", sourceNodeId = "n1", targetNodeId = "n2", label = null),
        ),
    )
}
