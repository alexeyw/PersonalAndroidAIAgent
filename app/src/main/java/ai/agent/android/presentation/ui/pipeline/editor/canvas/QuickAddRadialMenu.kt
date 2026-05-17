package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.pipelineeditor.glyph
import app.knotwork.design.components.pipelineeditor.headerOnColor
import app.knotwork.design.components.pipelineeditor.headerTint
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

private const val MENU_RADIUS_DP = 104f
private const val TILE_SIZE_DP = 44f
private const val TILE_LABEL_GAP_DP = 2f
private const val TILE_LABEL_WIDTH_DP = 60f

/**
 * Margin reserved around the menu when clamping the long-press anchor to the viewport
 * (radius + half-tile-width + a small visual breathing pad). Keeps the menu fully on
 * screen even when the user long-presses near the canvas edge.
 */
private const val MENU_EDGE_MARGIN_DP = MENU_RADIUS_DP + TILE_LABEL_WIDTH_DP / 2f + 8f

/**
 * 12-tile radial quick-add menu. Triggered by long-press on empty canvas space; tiles
 * arrange in a circle around the anchor and tapping a tile dispatches `onPick(type)`.
 *
 * Visual / behavioural contract: `node-specs.md` §canvas + `decisions.md §quick-add`.
 *
 * **Stateless** — caller toggles visibility through the `screenAnchor` parameter
 * (non-null shows the menu at that screen-space position; `null` hides it). The
 * scrim consumes outside taps via [onDismiss].
 *
 * Tile order matches `CatalogNodeType.entries` so a designer's snapshot reads the
 * same as the prototype.
 *
 * @param screenAnchorX screen-space X of the anchor (long-press point).
 * @param screenAnchorY screen-space Y of the anchor.
 * @param onPick invoked with the chosen domain [NodeType].
 * @param onDismiss invoked on scrim tap or back-press.
 * @param modifier optional layout modifier applied to the scrim root.
 */
@Composable
internal fun QuickAddRadialMenu(
    screenAnchorX: Float,
    screenAnchorY: Float,
    onPick: (NodeType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radius = with(density) { MENU_RADIUS_DP.dp.toPx() }
    val tilePx = with(density) { TILE_SIZE_DP.dp.toPx() }
    val marginPx = with(density) { MENU_EDGE_MARGIN_DP.dp.toPx() }
    // Track scrim size so we can clamp the anchor when the user long-presses near an edge,
    // keeping the entire 12-tile ring on screen instead of letting half of it overflow.
    var scrimSize by remember { mutableStateOf(IntSize.Zero) }
    val clampedAnchorX = if (scrimSize.width > 0) {
        screenAnchorX.coerceIn(marginPx, (scrimSize.width - marginPx).coerceAtLeast(marginPx))
    } else {
        screenAnchorX
    }
    val clampedAnchorY = if (scrimSize.height > 0) {
        screenAnchorY.coerceIn(marginPx, (scrimSize.height - marginPx).coerceAtLeast(marginPx))
    } else {
        screenAnchorY
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { scrimSize = it }
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
    ) {
        Text(
            text = stringResource(R.string.pipeline_editor_quick_add_title),
            style = KnotworkTextStyles.LabelMd,
            color = MaterialTheme.colorScheme.surface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = KnotworkTheme.spacing.sp6)
                .fillMaxWidth(),
        )
        val types = CatalogNodeType.entries
        val count = types.size
        val labelWidthPx = with(density) { TILE_LABEL_WIDTH_DP.dp.toPx() }
        types.forEachIndexed { index, type ->
            val theta = (TWO_PI * index / count) - HALF_PI
            // Each tile is rendered as a Column (circle + label) so the placed origin sits
            // at the column's top-left. Centre the column horizontally on the radial-anchor
            // by subtracting half the wider of (tile, label) — labels otherwise drift off
            // the radial path. Vertically anchor the circle's centre on the radius so the
            // label hangs below the circle without offsetting the perceived ring.
            val tileScreenX = clampedAnchorX + radius * cos(theta).toFloat() - labelWidthPx / 2f
            val tileScreenY = clampedAnchorY + radius * sin(theta).toFloat() - tilePx / 2f
            QuickAddTile(
                type = type,
                onPick = { onPick(NodeTypeMapper.toDomain(type)) },
                modifier = Modifier
                    .graphicsLayer {
                        translationX = tileScreenX
                        translationY = tileScreenY
                    },
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(KnotworkTheme.spacing.sp4)
                .semantics { contentDescription = "close" },
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.pipeline_editor_quick_add_close),
                tint = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun QuickAddTile(type: CatalogNodeType, onPick: () -> Unit, modifier: Modifier) {
    val tint = type.headerTint()
    val onTint = headerOnColor(tint)
    Column(
        modifier = modifier
            .width(TILE_LABEL_WIDTH_DP.dp)
            .clickable(onClick = onPick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP_DP.dp),
    ) {
        Surface(
            modifier = Modifier.size(TILE_SIZE_DP.dp).clip(CircleShape),
            color = tint,
            shape = CircleShape,
            tonalElevation = KnotworkTheme.elevation.el2,
            shadowElevation = KnotworkTheme.elevation.el2,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = type.glyph(),
                    contentDescription = quickAddShortLabel(type),
                    tint = onTint,
                    modifier = Modifier.size(GLYPH_SIZE_DP.dp),
                )
            }
        }
        Text(
            text = quickAddShortLabel(type),
            style = KnotworkTextStyles.LabelSm,
            color = MaterialTheme.colorScheme.surface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * Short, mixed-case labels for the radial-menu tiles. Distinct from
 * [CatalogNodeType.displayLabel] (which is UPPERCASE and used for the on-card header
 * strip) — radial tiles need a human-readable word that fits in ~72 dp at LabelSm.
 */
private fun quickAddShortLabel(type: CatalogNodeType): String = when (type) {
    CatalogNodeType.INPUT -> "Input"
    CatalogNodeType.OUTPUT -> "Output"
    CatalogNodeType.LITE_RT -> "Local LLM"
    CatalogNodeType.CLOUD -> "Cloud"
    CatalogNodeType.INTENT_ROUTER -> "Router"
    CatalogNodeType.IF_CONDITION -> "If"
    CatalogNodeType.CLARIFICATION -> "Ask user"
    CatalogNodeType.TOOL -> "Tool"
    CatalogNodeType.DECOMPOSITION -> "Decompose"
    CatalogNodeType.QUEUE_PROCESSOR -> "Queue"
    CatalogNodeType.EVALUATION -> "Evaluate"
    CatalogNodeType.SUMMARY -> "Summary"
}

private const val SCRIM_ALPHA = 0.5f
private const val TWO_PI = 2.0 * PI
private const val HALF_PI = PI / 2.0
private const val GLYPH_SIZE_DP = 20f
