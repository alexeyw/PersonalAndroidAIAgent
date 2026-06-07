package app.knotwork.android.presentation.ui.pipeline.editor.core

import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin projection from canvas space onto a mini-map overlay. Keeps
 * geometry separate from rendering so the math can be unit-tested without a
 * Compose host.
 *
 * Conventions:
 *  - "canvas" units are the same canvas-space pixels stored on `NodeModel.x / y`.
 *  - "mini" units are the pixel coordinates inside the mini-map body (the
 *    header strip is not part of [miniHeight]).
 *  - Projection preserves aspect ratio: the canvas bbox is uniformly scaled
 *    until either side touches the mini-map; the unused axis is centred.
 *
 * @property bbox canvas-space bounds enclosing every node (typically
 *   `Bounds.ofNodes(nodes, NODE_W, NODE_H)`).
 * @property miniWidth mini-map body width in pixels.
 * @property miniHeight mini-map body height in pixels.
 */
data class MiniMapGeometry(val bbox: Bounds, val miniWidth: Float, val miniHeight: Float) {

    /**
     * Uniform projection factor: 1 canvas px ↦ [scale] mini px. Clamped to
     * `Float.MIN_VALUE..1.0` (mini map never enlarges nodes past their canvas
     * size — the viewport rectangle would lose meaning otherwise).
     */
    val scale: Float by lazy {
        if (bbox.isEmpty || miniWidth <= 0f || miniHeight <= 0f) {
            0f
        } else {
            min(miniWidth / bbox.width, miniHeight / bbox.height)
        }
    }

    /** Horizontal offset that centres the projected bbox inside the mini-map body. */
    val originX: Float by lazy { (miniWidth - bbox.width * scale) / 2f }

    /** Vertical offset that centres the projected bbox inside the mini-map body. */
    val originY: Float by lazy { (miniHeight - bbox.height * scale) / 2f }

    /** Maps a canvas-space x onto the mini-map body x. */
    fun canvasToMiniX(x: Float): Float = originX + (x - bbox.minX) * scale

    /** Maps a canvas-space y onto the mini-map body y. */
    fun canvasToMiniY(y: Float): Float = originY + (y - bbox.minY) * scale

    /** Inverse projection: mini-map body x → canvas-space x. */
    fun miniToCanvasX(mx: Float): Float = if (scale == 0f) bbox.minX else bbox.minX + (mx - originX) / scale

    /** Inverse projection: mini-map body y → canvas-space y. */
    fun miniToCanvasY(my: Float): Float = if (scale == 0f) bbox.minY else bbox.minY + (my - originY) / scale

    /**
     * Projects the on-screen viewport onto the mini-map and returns the four
     * sides of the resulting rectangle in mini-pixel coordinates. The viewport
     * is given by [transform] + the viewport size in screen pixels.
     *
     * Edges are clamped to the mini-map body so the rectangle never bleeds
     * outside (it represents "the part of canvas currently visible" — anything
     * past the mini-map's bbox isn't visible by definition).
     */
    fun viewportRect(transform: CanvasTransform, viewportW: Float, viewportH: Float): MiniMapRect {
        if (scale == 0f) return MiniMapRect.Empty
        val canvasLeft = transform.screenToCanvasX(0f)
        val canvasTop = transform.screenToCanvasY(0f)
        val canvasRight = transform.screenToCanvasX(viewportW)
        val canvasBottom = transform.screenToCanvasY(viewportH)
        val left = canvasToMiniX(canvasLeft).coerceIn(0f, miniWidth)
        val top = canvasToMiniY(canvasTop).coerceIn(0f, miniHeight)
        val right = canvasToMiniX(canvasRight).coerceIn(0f, miniWidth)
        val bottom = canvasToMiniY(canvasBottom).coerceIn(0f, miniHeight)
        return MiniMapRect(
            left = min(left, right),
            top = min(top, bottom),
            right = max(left, right),
            bottom = max(top, bottom),
        )
    }
}

/**
 * Mini-pixel rectangle returned by [MiniMapGeometry.viewportRect].
 *
 * Empty (zero-area) when the geometry is degenerate; callers should
 * `if (rect.isEmpty) skip` to avoid drawing a 0×0 stroke.
 */
data class MiniMapRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    companion object {
        val Empty = MiniMapRect(0f, 0f, 0f, 0f)
    }
}
