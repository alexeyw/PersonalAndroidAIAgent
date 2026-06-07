package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Renders the canvas-space dot grid used as the editor background. Tracks
 * [transform] so the dots pan + zoom together with the rest of the canvas
 * (`canvas: 1200 × 1600 · 24 dp grid · 1.00×` as the info pill phrases it).
 *
 * The grid spacing matches [CanvasTransform.GRID_PX] — the same value used by
 * `snapToGrid` — so a snapped node always rests on a visible intersection.
 *
 * Drawn as canvas pixels rather than dp because the dots scale with [transform]
 * (zooming in makes them bigger; the dot count per visible square stays
 * constant in canvas-space terms).
 *
 * Performance: a 1× scale viewport of 1080 × 1920 px at 24 px grid renders
 * ~3600 dots per frame — small enough for a `Canvas` draw pass without
 * staggering or recycling.
 *
 * @param transform pan / zoom transform from the editor state.
 * @param modifier optional layout modifier (typically `.fillMaxSize()`).
 */
@Composable
internal fun DotGridBackground(transform: CanvasTransform, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val dotColor = Color(red = 0f, green = 0f, blue = 0f, alpha = DOT_ALPHA_LIGHT)
    val dotRadiusPx = with(density) { DOT_RADIUS_DP.dp.toPx() }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val gridScreen = CanvasTransform.GRID_PX * transform.scale
        if (gridScreen < MIN_VISIBLE_GRID_PX) return@Canvas
        // Find first grid line on or after the left/top edge of the viewport.
        val grid = CanvasTransform.GRID_PX
        val firstCanvasX = floor(transform.screenToCanvasX(0f) / grid) * grid
        val firstCanvasY = floor(transform.screenToCanvasY(0f) / grid) * grid
        val lastCanvasX = ceil(transform.screenToCanvasX(size.width) / grid) * grid
        val lastCanvasY = ceil(transform.screenToCanvasY(size.height) / grid) * grid
        var canvasX = firstCanvasX
        while (canvasX <= lastCanvasX) {
            var canvasY = firstCanvasY
            while (canvasY <= lastCanvasY) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadiusPx,
                    center = Offset(
                        x = transform.canvasToScreenX(canvasX),
                        y = transform.canvasToScreenY(canvasY),
                    ),
                )
                canvasY += CanvasTransform.GRID_PX
            }
            canvasX += CanvasTransform.GRID_PX
        }
    }
}

/** Visual radius of every grid dot; small enough not to compete with node ports. */
private const val DOT_RADIUS_DP = 1f

/** Dot tint alpha — kept low so the grid reads as a hint rather than a print. */
private const val DOT_ALPHA_LIGHT = 0.10f

/**
 * Below this projected grid step the dots fuse into noise. Hide them so the
 * canvas reads cleanly at extreme zoom-out (`0.4×`).
 */
private const val MIN_VISIBLE_GRID_PX = 6f
