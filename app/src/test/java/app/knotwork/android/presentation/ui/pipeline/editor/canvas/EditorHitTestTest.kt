package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.ui.unit.Density
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the editor's canvas-space hit-test geometry — the maths behind
 * connection creation. These cover the two regressions users hit:
 *
 *  - releasing a connection anywhere on the target node's card must connect (not only on a
 *    pixel-precise top-edge anchor), at any zoom — [hitTestInputNode];
 *  - a press on an outbound port must be recognised as a connection start so the canvas can
 *    decline to pan it — [hitTestOutboundPort].
 *
 * The pure geometry is exercised here (no Compose runtime); gesture arbitration itself is
 * device-only and out of scope for the JVM gate.
 */
class EditorHitTestTest {

    private val density = Density(density = 2f)

    private fun node(id: String, type: NodeType, x: Float, y: Float): NodeModel =
        NodeModel(id = id, type = type, x = x, y = y)

    // A LITE_RT card at canvas origin spans (0,0)..(336,192) at density 2 (168×96 dp).
    private val liteRt = node("n1", NodeType.LITE_RT, x = 0f, y = 0f)

    @Test
    fun `release on the node body connects to that node`() {
        // Card centre — the spot the old anchor-radius test missed at high zoom.
        val hit =
            hitTestInputNode(pointerCanvasX = 168f, pointerCanvasY = 96f, nodes = listOf(liteRt), density = density)
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `release near the bottom of the node body still connects`() {
        val hit =
            hitTestInputNode(pointerCanvasX = 40f, pointerCanvasY = 180f, nodes = listOf(liteRt), density = density)
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `release far from every node connects to nothing`() {
        val hit =
            hitTestInputNode(pointerCanvasX = 2000f, pointerCanvasY = 2000f, nodes = listOf(liteRt), density = density)
        assertNull(hit)
    }

    @Test
    fun `release between two nodes picks the nearer card`() {
        val far = node("n2", NodeType.LITE_RT, x = 1000f, y = 1000f)
        val hit =
            hitTestInputNode(
                pointerCanvasX = 168f,
                pointerCanvasY = 96f,
                nodes = listOf(liteRt, far),
                density = density,
            )
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `press on the single outbound port is recognised`() {
        // Default port anchor: (width/2, baseHeight) = (168, 128) at density 2.
        val hit = hitTestOutboundPort(
            pointerCanvasX = 168f,
            pointerCanvasY = 128f,
            nodes = listOf(liteRt),
            transform = CanvasTransform(scale = 1f),
            density = density,
        )
        assertNotNull(hit)
        assertEquals("n1", hit?.nodeId)
        assertEquals("", hit?.label)
    }

    @Test
    fun `press in empty canvas is not a port`() {
        val hit = hitTestOutboundPort(
            pointerCanvasX = 800f,
            pointerCanvasY = 800f,
            nodes = listOf(liteRt),
            transform = CanvasTransform(scale = 1f),
            density = density,
        )
        assertNull(hit)
    }

    @Test
    fun `port grab radius shrinks in canvas space as zoom increases`() {
        // 36 canvas px below the anchor: within tolerance at scale 1 (20dp*2=40px),
        // outside it at scale 2 (40/2=20px) — the grab area stays visually constant.
        val belowPort = 128f + 36f
        val atScale1 = hitTestOutboundPort(168f, belowPort, listOf(liteRt), CanvasTransform(scale = 1f), density)
        val atScale2 = hitTestOutboundPort(168f, belowPort, listOf(liteRt), CanvasTransform(scale = 2f), density)
        assertNotNull(atScale1)
        assertNull(atScale2)
    }

    @Test
    fun `IF node exposes two distinct outbound ports the press can target`() {
        val ifNode = node("if1", NodeType.IF_CONDITION, x = 0f, y = 0f)
        // Two ports sit symmetrically around the centre (168) at ±40 px (density 2).
        val left = hitTestOutboundPort(128f, 128f, listOf(ifNode), CanvasTransform(scale = 1f), density)
        val right = hitTestOutboundPort(208f, 128f, listOf(ifNode), CanvasTransform(scale = 1f), density)
        assertNotNull("Left True/False port must be grabbable", left)
        assertNotNull("Right True/False port must be grabbable", right)
        assertNotEquals("The two ports must carry different labels", left?.label, right?.label)
    }
}
