package app.knotwork.android.presentation.ui.pipeline.editor.core

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AutoLayoutTest {

    private fun node(type: NodeType, id: String = UUID.randomUUID().toString()): NodeModel =
        NodeModel(id = id, type = type, x = 0f, y = 0f, label = id)

    @Test
    fun `given empty graph when compute then empty result`() {
        val result = AutoLayout.compute(PipelineGraph(id = "p", name = "p"))
        assertTrue(result.positions.isEmpty())
        assertTrue(result.depths.isEmpty())
    }

    @Test
    fun `given linear graph when compute then depth increases monotonically`() {
        val a = node(NodeType.INPUT, "a")
        val b = node(NodeType.LITE_RT, "b")
        val c = node(NodeType.OUTPUT, "c")
        val graph = PipelineGraph(
            id = "g",
            name = "g",
            nodes = listOf(a, b, c),
            connections = listOf(
                ConnectionModel(id = "ab", sourceNodeId = "a", targetNodeId = "b"),
                ConnectionModel(id = "bc", sourceNodeId = "b", targetNodeId = "c"),
            ),
        )
        val result = AutoLayout.compute(graph)
        assertEquals(0, result.depths["a"])
        assertEquals(1, result.depths["b"])
        assertEquals(2, result.depths["c"])
    }

    @Test
    fun `given branching graph when compute then siblings share a depth and Y`() {
        val input = node(NodeType.INPUT, "in")
        val left = node(NodeType.LITE_RT, "left")
        val right = node(NodeType.LITE_RT, "right")
        val output = node(NodeType.OUTPUT, "out")
        val graph = PipelineGraph(
            id = "g",
            name = "g",
            nodes = listOf(input, left, right, output),
            connections = listOf(
                ConnectionModel(id = "1", sourceNodeId = "in", targetNodeId = "left"),
                ConnectionModel(id = "2", sourceNodeId = "in", targetNodeId = "right"),
                ConnectionModel(id = "3", sourceNodeId = "left", targetNodeId = "out"),
                ConnectionModel(id = "4", sourceNodeId = "right", targetNodeId = "out"),
            ),
        )
        val result = AutoLayout.compute(graph)
        assertEquals(result.depths["left"], result.depths["right"])
        val leftPos = result.positions.getValue("left")
        val rightPos = result.positions.getValue("right")
        assertEquals(leftPos.second, rightPos.second, 0f)
        assertNotEquals(leftPos.first, rightPos.first)
    }

    @Test
    fun `given any graph when compute then all positions snap to GRID_PX`() {
        val a = node(NodeType.INPUT, "a")
        val b = node(NodeType.LITE_RT, "b")
        val c = node(NodeType.OUTPUT, "c")
        val graph = PipelineGraph(
            id = "g",
            name = "g",
            nodes = listOf(a, b, c),
            connections = listOf(
                ConnectionModel(id = "ab", sourceNodeId = "a", targetNodeId = "b"),
                ConnectionModel(id = "bc", sourceNodeId = "b", targetNodeId = "c"),
            ),
        )
        val result = AutoLayout.compute(graph)
        result.positions.values.forEach { (x, y) ->
            assertEquals(0f, x % CanvasTransform.GRID_PX, 1e-3f)
            assertEquals(0f, y % CanvasTransform.GRID_PX, 1e-3f)
        }
    }

    @Test
    fun `given diamond merge when compute then merge node lands at deepest layer`() {
        val a = node(NodeType.INPUT, "a")
        val b = node(NodeType.LITE_RT, "b")
        val c = node(NodeType.LITE_RT, "c")
        val d = node(NodeType.OUTPUT, "d")
        val graph = PipelineGraph(
            id = "g",
            name = "g",
            nodes = listOf(a, b, c, d),
            connections = listOf(
                ConnectionModel(id = "1", sourceNodeId = "a", targetNodeId = "b"),
                ConnectionModel(id = "2", sourceNodeId = "a", targetNodeId = "c"),
                ConnectionModel(id = "3", sourceNodeId = "b", targetNodeId = "d"),
                ConnectionModel(id = "4", sourceNodeId = "c", targetNodeId = "d"),
            ),
        )
        val result = AutoLayout.compute(graph)
        assertEquals(0, result.depths["a"])
        assertEquals(1, result.depths["b"])
        assertEquals(1, result.depths["c"])
        assertEquals(2, result.depths["d"])
    }
}
