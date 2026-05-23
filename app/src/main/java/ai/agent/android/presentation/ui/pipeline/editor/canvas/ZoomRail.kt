package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

/**
 * Always-visible zoom rail anchored to the canvas right edge — three stacked
 * 40 dp tiles: `+` (zoom in), `−` (zoom out), and `⤡` (fit-to-view).
 *
 * Mockup reference: every canvas screenshot from the Phase 22 / Task 14 mockup
 * batch shows the rail in this exact position, regardless of editing /
 * validating / running / overview state. The rail is therefore part of the
 * canvas overlay (not the toolbar) and does not toggle.
 *
 * The composable is stateless; the caller passes the live transform-derived
 * enable flags ([canZoomIn] / [canZoomOut]) so the icons grey out at the
 * scale-range boundaries instead of becoming silent no-ops.
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ZoomTile(
            icon = Icons.Outlined.Add,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_zoom_in),
            enabled = canZoomIn,
            onClick = onZoomIn,
        )
        ZoomTile(
            icon = Icons.Outlined.Remove,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_zoom_out),
            enabled = canZoomOut,
            onClick = onZoomOut,
        )
        ZoomTile(
            icon = Icons.Outlined.OpenInFull,
            contentDescription = stringResource(R.string.pipeline_editor_zoom_rail_fit),
            enabled = canFit,
            onClick = onFit,
        )
    }
}

/** One 40 dp square tile shared by the three rail entries. */
@Composable
private fun ZoomTile(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (enabled) KnotworkTheme.extended.outlineStrong else KnotworkTheme.extended.divider
    val tint = if (enabled) KnotworkTheme.extended.onSurface2 else KnotworkTheme.extended.onSurfaceDim
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(TileSize)
            .background(color = KnotworkTheme.extended.surface1, shape = KnotworkTheme.shapes.md)
            .border(width = 1.dp, color = borderColor, shape = KnotworkTheme.shapes.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

private val TileSize = 40.dp
