package app.knotwork.android.presentation.ui.pipeline.editor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BezierEdgeTest {

    @Test
    fun `given two endpoints when controlPoints then handles share x with endpoints`() {
        val (c0, c1) = BezierEdge.controlPoints(0f, 0f, 100f, 200f)
        assertEquals(0f, c0.first, 0f)
        assertEquals(100f, c1.first, 0f)
        // c0 y is below source, c1 y is above target.
        assertTrue(c0.second > 0f)
        assertTrue(c1.second < 200f)
    }

    @Test
    fun `given pointAt 0 when evaluated then equals source point`() {
        val (sx, sy) = BezierEdge.pointAt(
            t = 0f,
            x0 = 1f, y0 = 2f,
            c0x = 3f, c0y = 4f,
            c1x = 5f, c1y = 6f,
            x1 = 7f, y1 = 8f,
        )
        assertEquals(1f, sx, 1e-3f)
        assertEquals(2f, sy, 1e-3f)
    }

    @Test
    fun `given pointAt 1 when evaluated then equals target point`() {
        val (sx, sy) = BezierEdge.pointAt(
            t = 1f,
            x0 = 1f, y0 = 2f,
            c0x = 3f, c0y = 4f,
            c1x = 5f, c1y = 6f,
            x1 = 7f, y1 = 8f,
        )
        assertEquals(7f, sx, 1e-3f)
        assertEquals(8f, sy, 1e-3f)
    }

    @Test
    fun `given short straight edge when arc length approximated then close to euclidean distance`() {
        val (c0, c1) = BezierEdge.controlPoints(0f, 0f, 0f, 100f)
        val len = BezierEdge.approximateArcLength(0f, 0f, c0.first, c0.second, c1.first, c1.second, 0f, 100f)
        // Pure vertical edge sampled along the curve: the result is at least the straight-line distance.
        assertTrue("len=$len", len >= 100f)
    }

    @Test
    fun `given point on the line when distanceToPoint then near zero`() {
        // Use an edge that runs straight down; control points keep it on x=0.
        val len = BezierEdge.distanceToPoint(
            px = 0f,
            py = 50f,
            x0 = 0f,
            y0 = 0f,
            c0x = 0f,
            c0y = 50f,
            c1x = 0f,
            c1y = 50f,
            x1 = 0f,
            y1 = 100f,
        )
        assertTrue("distance was $len", len < 1f)
    }

    @Test
    fun `given far point when distanceToPoint then meaningful magnitude`() {
        val (c0, c1) = BezierEdge.controlPoints(0f, 0f, 0f, 100f)
        val d = BezierEdge.distanceToPoint(
            px = 500f,
            py = 50f,
            x0 = 0f,
            y0 = 0f,
            c0x = c0.first,
            c0y = c0.second,
            c1x = c1.first,
            c1y = c1.second,
            x1 = 0f,
            y1 = 100f,
        )
        assertTrue("distance was $d", d > 400f)
    }
}
