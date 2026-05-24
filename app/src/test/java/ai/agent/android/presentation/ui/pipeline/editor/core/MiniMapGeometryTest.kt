package ai.agent.android.presentation.ui.pipeline.editor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for [MiniMapGeometry]. No Android dependency — runs on the JVM.
 */
class MiniMapGeometryTest {

    @Test
    fun `given non-empty bbox when projecting then scale fits the larger axis`() {
        val bbox = Bounds(minX = 0f, minY = 0f, maxX = 200f, maxY = 100f)
        val geom = MiniMapGeometry(bbox = bbox, miniWidth = 100f, miniHeight = 100f)
        // bbox is wider than tall — limiting axis is width (100 / 200 = 0.5).
        assertEquals(0.5f, geom.scale, 1e-3f)
    }

    @Test
    fun `given empty bbox when projecting then scale is zero`() {
        val empty = Bounds(minX = 50f, minY = 50f, maxX = 50f, maxY = 50f)
        val geom = MiniMapGeometry(bbox = empty, miniWidth = 100f, miniHeight = 100f)
        assertEquals(0f, geom.scale, 0f)
    }

    @Test
    fun `given canvas point when projecting then mini coordinates fall inside the mini bounds`() {
        val bbox = Bounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)
        val geom = MiniMapGeometry(bbox = bbox, miniWidth = 50f, miniHeight = 50f)
        val mx = geom.canvasToMiniX(50f)
        val my = geom.canvasToMiniY(50f)
        assertEquals(25f, mx, 1e-3f)
        assertEquals(25f, my, 1e-3f)
    }

    @Test
    fun `given mini point when inverse-projecting then canvas coordinates round-trip`() {
        val bbox = Bounds(minX = 100f, minY = 200f, maxX = 500f, maxY = 600f)
        val geom = MiniMapGeometry(bbox = bbox, miniWidth = 80f, miniHeight = 80f)
        val midCanvasX = 300f
        val midCanvasY = 400f
        val mx = geom.canvasToMiniX(midCanvasX)
        val my = geom.canvasToMiniY(midCanvasY)
        assertEquals(midCanvasX, geom.miniToCanvasX(mx), 1e-3f)
        assertEquals(midCanvasY, geom.miniToCanvasY(my), 1e-3f)
    }

    @Test
    fun `given identity transform and full-canvas viewport when viewportRect then covers entire mini bbox`() {
        val bbox = Bounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)
        val geom = MiniMapGeometry(bbox = bbox, miniWidth = 50f, miniHeight = 50f)
        // Identity transform + viewport == bbox in screen space.
        val rect = geom.viewportRect(CanvasTransform(scale = geom.scale), viewportW = 50f, viewportH = 50f)
        assertEquals(0f, rect.left, 1e-2f)
        assertEquals(0f, rect.top, 1e-2f)
        assertEquals(50f, rect.right, 1e-2f)
        assertEquals(50f, rect.bottom, 1e-2f)
    }

    @Test
    fun `given off-canvas viewport when viewportRect then clamps to mini bbox`() {
        val bbox = Bounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)
        val geom = MiniMapGeometry(bbox = bbox, miniWidth = 50f, miniHeight = 50f)
        // Pan way to the right so the viewport sees canvas (-1000..-900) — outside bbox entirely.
        val transform = CanvasTransform(scale = 1f, offsetX = 1000f, offsetY = 0f)
        val rect = geom.viewportRect(transform, viewportW = 100f, viewportH = 100f)
        // Both edges land outside [0, miniWidth=50]; clamping zeros the rect.
        assertTrue("Expected clamped rect to be empty, got: $rect", rect.isEmpty)
    }

    @Test
    fun `given degenerate geometry when viewportRect then returns empty`() {
        val empty = Bounds(minX = 0f, minY = 0f, maxX = 0f, maxY = 0f)
        val geom = MiniMapGeometry(bbox = empty, miniWidth = 50f, miniHeight = 50f)
        val rect = geom.viewportRect(CanvasTransform(), viewportW = 100f, viewportH = 100f)
        assertTrue(rect.isEmpty)
    }
}
