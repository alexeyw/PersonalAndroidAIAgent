package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.pipelineio.PipelineBundleTestFixtures.linearGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PipelineBundleIdRemapper] — the "import as copy" id rewrite. The
 * rewrite must regenerate every pipeline / node / connection id and remap
 * intra-bundle references so a copied composition never points back at the
 * originals.
 */
class PipelineBundleIdRemapperTest {

    /** Deterministic generator: appends a monotonic suffix to each old id. */
    private fun counterGenerator(): (String) -> String {
        var counter = 0
        return { old -> "$old#${counter++}" }
    }

    @Test
    fun `regenerate rewrites pipeline ids and remaps intra-bundle targets`() {
        val root = linearGraph("root", targets = listOf("sub"))
        val sub = linearGraph("sub")

        val (newRoot, newSub) = PipelineBundleIdRemapper.regenerate(listOf(root, sub), counterGenerator())

        // Pipeline ids changed.
        assertFalse(newRoot.id == "root")
        assertFalse(newSub.id == "sub")
        // The PIPELINE node's target now points at the copied sub-pipeline's new id.
        val target = newRoot.nodes.first { it.type == NodeType.PIPELINE }.targetPipelineId
        assertEquals(newSub.id, target)
    }

    @Test
    fun `regenerate rewrites node and connection ids and keeps endpoints consistent`() {
        val graph = linearGraph("root", targets = listOf("sub"))

        val copy = PipelineBundleIdRemapper.regenerate(listOf(graph), counterGenerator()).single()

        val oldNodeIds = graph.nodes.map { it.id }.toSet()
        assertTrue("every node id must be fresh", copy.nodes.none { it.id in oldNodeIds })
        // Every connection endpoint still resolves to a node in the copied graph.
        val copyNodeIds = copy.nodes.map { it.id }.toSet()
        assertTrue(
            "connection endpoints must remap to copied node ids",
            copy.connections.all { it.sourceNodeId in copyNodeIds && it.targetNodeId in copyNodeIds },
        )
    }

    @Test
    fun `regenerate leaves the copied graph internally valid`() {
        val graph = linearGraph("root")

        val copy = PipelineBundleIdRemapper.regenerate(listOf(graph), counterGenerator()).single()

        assertTrue("a remapped valid graph stays valid", copy.validate().isEmpty())
    }

    @Test
    fun `freshenElementIds keeps pipeline ids and targets but regenerates node and connection ids`() {
        val root = linearGraph("root", targets = listOf("sub"))
        val sub = linearGraph("sub")

        val (newRoot, newSub) = PipelineBundleIdRemapper.freshenElementIds(listOf(root, sub), counterGenerator())

        // Pipeline ids and the PIPELINE target are preserved (REPLACE = sync).
        assertEquals("root", newRoot.id)
        assertEquals("sub", newSub.id)
        assertEquals("sub", newRoot.nodes.first { it.type == NodeType.PIPELINE }.targetPipelineId)
        // Node ids are fresh, endpoints stay consistent, graph stays valid.
        assertTrue(newRoot.nodes.none { it.id in root.nodes.map { n -> n.id } })
        assertTrue(newRoot.validate().isEmpty())
    }

    @Test
    fun `freshenElementIds makes node ids unique across pipelines that reused them`() {
        val a = linearGraph("a").copy(
            nodes = listOf(
                NodeModel(id = "node-1", type = NodeType.INPUT, x = 0f, y = 0f),
                NodeModel(id = "node-2", type = NodeType.OUTPUT, x = 0f, y = 0f),
            ),
            connections = emptyList(),
        )
        val b = a.copy(id = "b")

        val out = PipelineBundleIdRemapper.freshenElementIds(listOf(a, b), counterGenerator())

        val nodeIds = out.flatMap { g -> g.nodes.map { it.id } }
        assertEquals(nodeIds.size, nodeIds.toSet().size)
    }
}
