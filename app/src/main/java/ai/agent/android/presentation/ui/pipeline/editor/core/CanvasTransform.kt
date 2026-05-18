package ai.agent.android.presentation.ui.pipeline.editor.core

import kotlin.math.round

/**
 * Affine transform between two coordinate spaces: an infinite "canvas" plane where node
 * positions are stored, and the on-screen viewport.
 *
 * The transform is uniform-scale + translation only — there is no rotation or shear in
 * the editor — so it is encoded as `(scale, offsetX, offsetY)` rather than a full 3×3
 * matrix. All math is pure Kotlin so it can be unit-tested off the device.
 *
 * `screen = canvas * scale + offset` ⇔ `canvas = (screen - offset) / scale`
 *
 * Bounds match `node-specs.md` §canvas: scale clamped to `[0.4f, 2.0f]`.
 *
 * @property scale uniform scale factor; `1.0f` means 1 canvas-px == 1 screen-px.
 * @property offsetX horizontal screen-space translation applied after scaling.
 * @property offsetY vertical screen-space translation applied after scaling.
 */
data class CanvasTransform(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {

    /**
     * Maps a canvas-space point onto screen pixels.
     */
    fun canvasToScreenX(x: Float): Float = x * scale + offsetX

    /** @see canvasToScreenX */
    fun canvasToScreenY(y: Float): Float = y * scale + offsetY

    /**
     * Maps a screen-pixel point back to canvas space.
     */
    fun screenToCanvasX(x: Float): Float = (x - offsetX) / scale

    /** @see screenToCanvasX */
    fun screenToCanvasY(y: Float): Float = (y - offsetY) / scale

    /**
     * Returns a copy with [scale] adjusted by [factor] around the screen-space anchor
     * `(anchorX, anchorY)`. The anchor is the pinch midpoint — the canvas point under
     * the user's fingers stays fixed while the surroundings scale.
     *
     * @param factor multiplicative delta from a gesture handler (`zoom` parameter on
     * `transformable`). Clamped so the resulting scale stays within [MIN_SCALE]..[MAX_SCALE].
     * @param anchorX screen-X of the pinch midpoint.
     * @param anchorY screen-Y of the pinch midpoint.
     */
    fun zoomedBy(factor: Float, anchorX: Float, anchorY: Float): CanvasTransform {
        val nextScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (nextScale == scale) return this
        val canvasX = screenToCanvasX(anchorX)
        val canvasY = screenToCanvasY(anchorY)
        return copy(
            scale = nextScale,
            offsetX = anchorX - canvasX * nextScale,
            offsetY = anchorY - canvasY * nextScale,
        )
    }

    /**
     * Returns a copy translated by a screen-space delta. Used by the canvas pan handler.
     */
    fun panBy(dxScreen: Float, dyScreen: Float): CanvasTransform =
        copy(offsetX = offsetX + dxScreen, offsetY = offsetY + dyScreen)

    /**
     * Returns a copy whose viewport is centred on the canvas-space point `(x, y)`.
     * The resulting transform keeps the current [scale]; the offset is recomputed
     * so the named point lands at `(viewportW / 2, viewportH / 2)` in screen space.
     *
     * @param x canvas-X to centre on.
     * @param y canvas-Y to centre on.
     * @param viewportW viewport width in pixels.
     * @param viewportH viewport height in pixels.
     */
    fun centeredOn(x: Float, y: Float, viewportW: Float, viewportH: Float): CanvasTransform = copy(
        offsetX = viewportW / 2f - x * scale,
        offsetY = viewportH / 2f - y * scale,
    )

    companion object {
        /** Lower bound for [scale] per `node-specs.md` §canvas. */
        const val MIN_SCALE: Float = 0.4f

        /** Upper bound for [scale] per `node-specs.md` §canvas. */
        const val MAX_SCALE: Float = 2.0f

        /** Canvas-space grid spacing for snap-to-grid (`node-specs.md` §Drag-and-drop). */
        const val GRID_PX: Float = 24f

        /**
         * Snaps [value] to the nearest [GRID_PX] step. Pure helper used both at drag-release
         * commit time and by the auto-layout module so every node position is grid-aligned.
         */
        fun snapToGrid(value: Float): Float = round(value / GRID_PX) * GRID_PX
    }
}
