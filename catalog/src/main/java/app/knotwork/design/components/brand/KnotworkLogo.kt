@file:Suppress("MatchingDeclarationName") // Hosts KnotworkLogo + KnotworkLogoSize enum.

package app.knotwork.design.components.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

/**
 * Visual size of the Knotwork brand mark. Translates to a square canvas
 * whose side length is [size]; the mark is drawn centred inside it.
 */
enum class KnotworkLogoSize(val size: Dp) {
    /** 32 dp — inline rows (e.g. TopAppBar leading). */
    Sm(LOGO_SM),

    /** 64 dp — default for screen hero / drawer header. */
    Md(LOGO_MD),

    /** 128 dp — large splash / onboarding hero. */
    Lg(LOGO_LG),
}

private val LOGO_SM = 32.dp
private val LOGO_MD = 64.dp
private val LOGO_LG = 128.dp

/** Stroke width as a fraction of the canvas side (so all sizes look proportionally identical). */
private const val STROKE_FRACTION = 0.045f

/** Inset between the outer square and the canvas edge (fraction of side). */
private const val OUTER_INSET_FRACTION = 0.12f

/** Inset of the inner diamond relative to the canvas centre (fraction of side). */
private const val DIAMOND_INSET_FRACTION = 0.30f

/** Corner-radius of the outer rounded square (fraction of side). */
private const val OUTER_CORNER_FRACTION = 0.20f

/**
 * Geometric Knotwork brand mark.
 *
 * Visual contract: a rounded square frame with an inner diamond (rotated
 * 45°) — a minimalist nod to the interlaced-knot motif that gives the
 * design system its name. Renders as a single-colour stroke (no fills),
 * so it composes cleanly over any background in either theme.
 *
 * The mark is purely Compose-vector based: no raster assets, no
 * Material-icon dependencies. All sizes share an identical visual
 * weight by deriving stroke width as a fraction of the canvas side.
 *
 * @param modifier additional layout modifier applied to the root canvas.
 * @param size visual size token; defaults to [KnotworkLogoSize.Md].
 * @param tint stroke colour; defaults to `MaterialTheme.colorScheme.primary`.
 * Pass `KnotworkTheme.extended.onSurfaceMuted` for monochrome rendering.
 */
@Composable
fun KnotworkLogo(
    modifier: Modifier = Modifier,
    size: KnotworkLogoSize = KnotworkLogoSize.Md,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val sideDp = size.size
    Canvas(modifier = modifier.size(sideDp)) {
        val side = this.size.minDimension
        val stroke = side * STROKE_FRACTION
        val inset = side * OUTER_INSET_FRACTION
        val cornerRadius = side * OUTER_CORNER_FRACTION
        val centre = Offset(side / 2f, side / 2f)
        val diamondInset = side * DIAMOND_INSET_FRACTION

        // Outer rounded square (manual Path so we don't depend on `drawRoundRect`'s
        // pixel-rounding behaviour across API levels).
        val outerSide = side - inset * 2f
        val outerPath = Path().apply {
            addRoundedSquare(
                origin = Offset(inset, inset),
                edge = outerSide,
                radius = cornerRadius,
            )
        }
        drawPath(path = outerPath, color = tint, style = Stroke(width = stroke))

        // Inner diamond — drawn as a square rotated 45° around the canvas centre.
        val diamondSide = side - diamondInset * 2f
        rotate(degrees = 45f, pivot = centre) {
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(centre.x - diamondSide / 2f, centre.y - diamondSide / 2f),
                size = Size(diamondSide, diamondSide),
                style = Stroke(width = stroke),
            )
            // Re-draw via path so the stroke colour is applied (drawRect with
            // Color.Transparent paints nothing — we need a real-colour stroke).
            val diamondPath = Path().apply {
                moveTo(centre.x - diamondSide / 2f, centre.y - diamondSide / 2f)
                lineTo(centre.x + diamondSide / 2f, centre.y - diamondSide / 2f)
                lineTo(centre.x + diamondSide / 2f, centre.y + diamondSide / 2f)
                lineTo(centre.x - diamondSide / 2f, centre.y + diamondSide / 2f)
                close()
            }
            drawPath(path = diamondPath, color = tint, style = Stroke(width = stroke))
        }
    }
}

/**
 * Appends a rounded square path with [edge]-length sides at [origin],
 * with corner radius [radius] applied to all four corners.
 */
private fun Path.addRoundedSquare(origin: Offset, edge: Float, radius: Float) {
    val r = radius.coerceAtMost(edge / 2f)
    val x0 = origin.x
    val y0 = origin.y
    val x1 = origin.x + edge
    val y1 = origin.y + edge
    moveTo(x0 + r, y0)
    lineTo(x1 - r, y0)
    quadraticTo(x1, y0, x1, y0 + r)
    lineTo(x1, y1 - r)
    quadraticTo(x1, y1, x1 - r, y1)
    lineTo(x0 + r, y1)
    quadraticTo(x0, y1, x0, y1 - r)
    lineTo(x0, y0 + r)
    quadraticTo(x0, y0, x0 + r, y0)
    close()
}

/** Convenience preview default for non-snapshot call sites that just want a tinted brand. */
@Suppress("unused") // Public API for downstream consumers (about / splash mappers).
@Composable
fun knotworkBrandTint(): Color = KnotworkTheme.extended.onSurface2
