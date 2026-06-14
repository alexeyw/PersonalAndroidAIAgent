package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.primaryConstructor

/**
 * Unit tests for [PipelineGraph.contentHash] — the checkpoint-invalidation
 * contract of the persistent pipeline-run records. The hash must be stable
 * under cosmetic edits (canvas moves, renames of the pipeline, list
 * reordering) and must change for every execution-relevant edit (prompts,
 * tool bindings, routing).
 */
class PipelineGraphContentHashTest {

    private fun baseGraph(): PipelineGraph = PipelineGraph(
        id = "pipe-1",
        name = "Base",
        nodes = listOf(
            NodeModel("input_1", NodeType.INPUT, 0f, 0f),
            NodeModel("llm_1", NodeType.LITE_RT, 100f, 50f, systemPrompt = "Answer briefly."),
            NodeModel("output_1", NodeType.OUTPUT, 200f, 100f, systemPrompt = null),
        ),
        connections = listOf(
            ConnectionModel("c1", "input_1", "llm_1"),
            ConnectionModel("c2", "llm_1", "output_1"),
        ),
        updatedAt = 1_000L,
    )

    @Test
    fun `hash is deterministic for equal graphs`() {
        assertEquals(baseGraph().contentHash(), baseGraph().contentHash())
    }

    @Test
    fun `hash is a 64-char lowercase hex string`() {
        val hash = baseGraph().contentHash()

        assertEquals(64, hash.length)
        assertTrue("Not lowercase hex: $hash", hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `moving a node on the canvas does not change the hash`() {
        val moved = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map { it.copy(x = it.x + 500f, y = it.y + 300f) },
            )
        }

        assertEquals(baseGraph().contentHash(), moved.contentHash())
    }

    @Test
    fun `renaming the pipeline or touching updatedAt does not change the hash`() {
        val renamed = baseGraph().copy(id = "pipe-2", name = "Renamed", updatedAt = 9_999L)

        assertEquals(baseGraph().contentHash(), renamed.contentHash())
    }

    @Test
    fun `reordering nodes and connections does not change the hash`() {
        val reordered = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.reversed(),
                connections = graph.connections.reversed(),
            )
        }

        assertEquals(baseGraph().contentHash(), reordered.contentHash())
    }

    @Test
    fun `editing a system prompt changes the hash`() {
        val edited = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map {
                    if (it.id == "llm_1") it.copy(systemPrompt = "Answer at length.") else it
                },
            )
        }

        assertNotEquals(baseGraph().contentHash(), edited.contentHash())
    }

    @Test
    fun `changing a tool binding changes the hash`() {
        val edited = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map {
                    if (it.id == "llm_1") it.copy(toolName = "web.search") else it
                },
            )
        }

        assertNotEquals(baseGraph().contentHash(), edited.contentHash())
    }

    @Test
    fun `changing a target pipeline binding changes the hash`() {
        val edited = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map {
                    if (it.id == "llm_1") it.copy(targetPipelineId = "other-pipeline") else it
                },
            )
        }

        assertNotEquals(baseGraph().contentHash(), edited.contentHash())
    }

    @Test
    fun `adding a node changes the hash`() {
        val extended = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes + NodeModel("summary_1", NodeType.SUMMARY, 0f, 0f),
            )
        }

        assertNotEquals(baseGraph().contentHash(), extended.contentHash())
    }

    @Test
    fun `rewiring a connection or its routing label changes the hash`() {
        val rewired = baseGraph().let { graph ->
            graph.copy(
                connections = graph.connections.map {
                    if (it.id == "c2") it.copy(label = "True") else it
                },
            )
        }

        assertNotEquals(baseGraph().contentHash(), rewired.contentHash())
    }

    /**
     * Drift guard: contentHash() hand-enumerates the model fields, so every
     * field added to [NodeModel] / [ConnectionModel] / [PipelineGraph] MUST
     * be classified — either appended to the canonical serialization or
     * added to the documented exclusion set below. This test fails the build
     * until the author makes that call, preventing the silent-stale-hash
     * failure mode where an execution-relevant field is edited between
     * interruption and resume without invalidating the checkpoint.
     */
    @Test
    fun `contentHash enumeration tracks every model field`() {
        val hashedNodeFields = setOf(
            "id", "type", "label", "toolName", "targetPipelineId", "modelPath", "conditionComplexity",
            "conditionKeywords", "conditionPrompt", "systemPrompt", "cloudProvider",
            "clarificationTimeoutMs", "contextConfig", "configJson",
        )
        val excludedNodeFields = setOf("x", "y")
        assertEquals(
            "NodeModel gained or lost a field — classify it in PipelineGraph.contentHash() " +
                "(hash it, or document the exclusion) and update this guard.",
            hashedNodeFields + excludedNodeFields,
            constructorParamNames(NodeModel::class),
        )

        val hashedConnectionFields = setOf("id", "sourceNodeId", "targetNodeId", "label")
        assertEquals(
            "ConnectionModel gained or lost a field — classify it in PipelineGraph.contentHash() " +
                "and update this guard.",
            hashedConnectionFields,
            constructorParamNames(ConnectionModel::class),
        )

        val hashedGraphFields = setOf("nodes", "connections")
        val excludedGraphFields = setOf("id", "name", "updatedAt")
        assertEquals(
            "PipelineGraph gained or lost a field — classify it in contentHash() " +
                "and update this guard.",
            hashedGraphFields + excludedGraphFields,
            constructorParamNames(PipelineGraph::class),
        )
    }

    private fun constructorParamNames(klass: kotlin.reflect.KClass<*>): Set<String> =
        klass.primaryConstructor!!.parameters.mapNotNull { it.name }.toSet()

    @Test
    fun `field shifting between adjacent columns cannot collide`() {
        // Same concatenated text, split differently across two adjacent
        // fields — the control-character separators must keep them distinct.
        val graphA = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map {
                    if (it.id == "llm_1") it.copy(conditionKeywords = "ab", conditionPrompt = "c") else it
                },
            )
        }
        val graphB = baseGraph().let { graph ->
            graph.copy(
                nodes = graph.nodes.map {
                    if (it.id == "llm_1") it.copy(conditionKeywords = "a", conditionPrompt = "bc") else it
                },
            )
        }

        assertNotEquals(graphA.contentHash(), graphB.contentHash())
    }
}
