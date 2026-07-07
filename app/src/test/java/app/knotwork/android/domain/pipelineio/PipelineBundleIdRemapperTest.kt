package app.knotwork.android.domain.pipelineio

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
}
