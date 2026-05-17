package ai.agent.android.presentation.ui.pipeline.editor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-Kotlin tests for [CanvasTransform]. No Android dependency — runs on the JVM.
 */
class CanvasTransformTest {

    @Test
    fun `given identity transform when round-trip then point is preserved`() {
        val t = CanvasTransform()
        val x = 123.4f
        val y = -56.7f
        assertEquals(x, t.screenToCanvasX(t.canvasToScreenX(x)), 1e-3f)
        assertEquals(y, t.screenToCanvasY(t.canvasToScreenY(y)), 1e-3f)
    }

    @Test
    fun `given scaled transform when round-trip then point is preserved`() {
        val t = CanvasTransform(scale = 1.5f, offsetX = 20f, offsetY = -10f)
        assertEquals(50f, t.screenToCanvasX(t.canvasToScreenX(50f)), 1e-3f)
        assertEquals(80f, t.screenToCanvasY(t.canvasToScreenY(80f)), 1e-3f)
    }

    @Test
    fun `given zoom factor above max when zoomedBy then scale clamps to MAX_SCALE`() {
        val t = CanvasTransform(scale = 1.9f)
        val clamped = t.zoomedBy(factor = 5f, anchorX = 0f, anchorY = 0f)
        assertEquals(CanvasTransform.MAX_SCALE, clamped.scale, 0f)
    }

    @Test
    fun `given zoom factor below min when zoomedBy then scale clamps to MIN_SCALE`() {
        val t = CanvasTransform(scale = 0.5f)
        val clamped = t.zoomedBy(factor = 0.1f, anchorX = 0f, anchorY = 0f)
        assertEquals(CanvasTransform.MIN_SCALE, clamped.scale, 0f)
    }

    @Test
    fun `given zoom around anchor when zoomedBy then anchor canvas point lands at same screen point`() {
        val t = CanvasTransform(scale = 1f, offsetX = 0f, offsetY = 0f)
        val anchorScreenX = 200f
        val anchorScreenY = 150f
        val zoomed = t.zoomedBy(factor = 1.5f, anchorX = anchorScreenX, anchorY = anchorScreenY)
        val canvasAnchorX = t.screenToCanvasX(anchorScreenX)
        val canvasAnchorY = t.screenToCanvasY(anchorScreenY)
        assertEquals(anchorScreenX, zoomed.canvasToScreenX(canvasAnchorX), 1e-3f)
        assertEquals(anchorScreenY, zoomed.canvasToScreenY(canvasAnchorY), 1e-3f)
    }

    @Test
    fun `given pan delta when panBy then offsets shift by exactly that delta`() {
        val t = CanvasTransform(scale = 1.5f, offsetX = 10f, offsetY = 20f)
        val panned = t.panBy(dxScreen = 25f, dyScreen = -10f)
        assertEquals(35f, panned.offsetX, 0f)
        assertEquals(10f, panned.offsetY, 0f)
        assertEquals(1.5f, panned.scale, 0f)
    }

    @Test
    fun `given canvas-space anchor when centeredOn then anchor projects to viewport centre`() {
        val t = CanvasTransform(scale = 2f).centeredOn(x = 100f, y = 50f, viewportW = 400f, viewportH = 300f)
        assertEquals(200f, t.canvasToScreenX(100f), 1e-3f)
        assertEquals(150f, t.canvasToScreenY(50f), 1e-3f)
    }

    @Test
    fun `given value mid-grid when snapToGrid then rounds to nearest grid step`() {
        assertEquals(0f, CanvasTransform.snapToGrid(11f), 0f)
        assertEquals(24f, CanvasTransform.snapToGrid(13f), 0f)
        assertEquals(24f, CanvasTransform.snapToGrid(35f), 0f)
        assertEquals(48f, CanvasTransform.snapToGrid(37f), 0f)
        assertEquals(-24f, CanvasTransform.snapToGrid(-13f), 0f)
    }

    @Test
    fun `given identity-scale factor when zoomedBy then transform stays identical`() {
        val t = CanvasTransform(scale = 1f, offsetX = 5f, offsetY = 5f)
        val next = t.zoomedBy(factor = 1f, anchorX = 100f, anchorY = 100f)
        assertEquals(t, next)
    }

    @Test
    fun `given zero scale factor when zoomedBy then never produces NaN offsets`() {
        val t = CanvasTransform(scale = 1f)
        val next = t.zoomedBy(factor = 0.1f, anchorX = 50f, anchorY = 50f)
        assertNotEquals(Float.NaN, next.offsetX)
        assertNotEquals(Float.NaN, next.offsetY)
    }
}
