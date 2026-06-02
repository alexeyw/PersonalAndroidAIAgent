package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Shared builders used by every custom `AppIcons.*` ImageVector under this
 * package. Centralising them keeps each icon file down to the SVG path data
 * itself, which is what gets reviewed when an icon is updated.
 *
 * Path colours are deliberately set to [Color.Black]: the
 * [androidx.compose.material3.Icon] composable applies a colour filter from
 * its `tint` parameter that overrides whatever the vector declared, so the
 * black is just a stand-in for "currentColor" in the SVG source.
 */

internal const val ICON_VIEWPORT = 24f
internal val ICON_SIZE = 24.dp

/**
 * Stroke-width tokens (spec §0.7). The vector path geometry bakes in the
 * default weight; [IconStroke.ACTIVE] / [IconStroke.CONTEXTUAL] are applied by
 * restroked icon variants (selected bottom-nav tab + active segmented control →
 * Active; select-mode app-bar actions → Contextual).
 */
internal object IconStroke {
    const val DEFAULT = 1.6f
    const val ACTIVE = 2.0f
    const val CONTEXTUAL = 1.7f
}

/**
 * Starts a 24×24 [ImageVector.Builder] with the canonical Knotwork defaults
 * (matches the SVGs in `project_docs/design/icons-src/`).
 */
internal fun iconBuilder(name: String): ImageVector.Builder = ImageVector.Builder(
    name = name,
    defaultWidth = ICON_SIZE,
    defaultHeight = ICON_SIZE,
    viewportWidth = ICON_VIEWPORT,
    viewportHeight = ICON_VIEWPORT,
)

/**
 * Adds a stroked path matching the project's standard stroke style
 * ([IconStroke.DEFAULT] = 1.6 px optical width, round caps/joins, currentColor).
 *
 * @param d SVG-compatible path data string. Parsed via [addPathNodes].
 * @param strokeWidth Optional override; defaults to [IconStroke.DEFAULT].
 * @param strokeAlpha Optional alpha multiplier for the stroke (decorative
 *   helper lines use `0.3` in `auto-layout.svg`).
 */
internal fun ImageVector.Builder.strokePath(
    d: String,
    strokeWidth: Float = IconStroke.DEFAULT,
    strokeAlpha: Float = 1f,
): ImageVector.Builder = addPath(
    pathData = addPathNodes(d),
    stroke = SolidColor(Color.Black),
    strokeAlpha = strokeAlpha,
    strokeLineWidth = strokeWidth,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)

/**
 * Adds a solid-fill path in `currentColor`. Used for circles/triangles that
 * the SVG source rendered with `fill="currentColor" stroke="none"`.
 */
internal fun ImageVector.Builder.fillPath(d: String): ImageVector.Builder = addPath(
    pathData = addPathNodes(d),
    fill = SolidColor(Color.Black),
)

/**
 * Returns SVG path data for a circle at ([cx], [cy]) with radius [r] using
 * two half-arcs (closed). The result works identically for stroked or filled
 * paths.
 */
internal fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r} $cy a $r $r 0 1 0 ${r * 2} 0 a $r $r 0 1 0 ${-r * 2} 0 Z"

/**
 * Returns SVG path data for a rectangle at ([x], [y]) with width [w], height
 * [h], and uniform corner radius [rx] (set to `0f` for a sharp rectangle).
 */
internal fun roundedRectPath(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
    if (rx <= 0f) {
        return "M $x $y h $w v $h h ${-w} Z"
    }
    val innerW = w - 2f * rx
    val innerH = h - 2f * rx
    return buildString {
        append("M ${x + rx} $y ")
        append("h $innerW ")
        append("a $rx $rx 0 0 1 $rx $rx ")
        append("v $innerH ")
        append("a $rx $rx 0 0 1 ${-rx} $rx ")
        append("h ${-innerW} ")
        append("a $rx $rx 0 0 1 ${-rx} ${-rx} ")
        append("v ${-innerH} ")
        append("a $rx $rx 0 0 1 $rx ${-rx} Z")
    }
}
