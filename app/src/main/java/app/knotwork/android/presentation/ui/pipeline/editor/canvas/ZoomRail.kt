package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.knotwork.android.R
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme

/**
 * Always-visible zoom rail anchored to the canvas right edge — three stacked
 * 40 dp tiles: `+` (zoom in), `−` (zoom out), and `⤡` (fit-to-view).
 *
 * The rail keeps this exact position regardless of editing /
 * validating / running / overview state. The rail is therefore part of the
 * canvas overlay (not the toolbar) and does not toggle.
 *
 * The composable is stateless; the caller passes the live transform-derived
 * enable flags ([canZoomIn] / [canZoomOut]) so the icons grey out at the
 * scale-range boundaries instead of becoming silent no-ops.
 *
 * **Tile sizing.** `IconButton` enforces
 * a 48 dp minimum interactive size that overrode the 40 dp tile constraint —
 * adjacent tiles visibly overlapped. The rail now uses `Surface + clickable +
 * Icon` directly so the 40 dp tile size is respected. The 4 dp inter-tile gap
 * lands cleanly.
 *
 * @param onZoomIn invoked when the `+` tile is tapped.
 * @param onZoomOut invoked when the `−` tile is tapped.
 * @param onFit invoked when the `⤡` tile is tapped (Fit-to-view).
 * @param canZoomIn gates the `+` tile — typically `transform.scale <
 *   CanvasTransform.MAX_SCALE`.
 * @param canZoomOut gates the `−` tile — typically `transform.scale >
 *   CanvasTransform.MIN_SCALE`.
 * @param canFit gates the `⤡` tile — typically `graph.nodes.isNotEmpty()`.
 * @param modifier optional layout modifier applied to the rail root (use the
 *   parent `Box` `Alignment.TopEnd` to position it).
 */
@Composable
fun ZoomRail(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    canFit: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        ZoomTile(
            icon = AppIcons.Add,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_zoom_in),
            enabled = canZoomIn,
            onClick = onZoomIn,
        )
        ZoomTile(
            icon = AppIcons.Minus,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_zoom_out),
            enabled = canZoomOut,
            onClick = onZoomOut,
        )
        ZoomTile(
            icon = AppIcons.Expand,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_fit),
            enabled = canFit,
            onClick = onFit,
        )
    }
}

/**
 * One 40 dp square tile shared by the three rail entries. Implemented as
 * `Surface + clickable + Icon` (not `IconButton`) so the 40 dp tile constraint
 * survives Material3's 48 dp minimum-interactive-component enforcement.
 */
@Composable
private fun ZoomTile(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (enabled) KnotworkTheme.extended.outlineStrong else KnotworkTheme.extended.divider
    val tint = if (enabled) KnotworkTheme.extended.onSurface2 else KnotworkTheme.extended.onSurfaceDim
    Surface(
        modifier = Modifier
            .size(TileSize)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                onClick(label = contentDescription, action = null)
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = KnotworkTheme.extended.surface1,
        shape = KnotworkTheme.shapes.md,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}

private val TileSize = 40.dp
private val TileGap = 4.dp
