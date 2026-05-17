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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.pipelineeditor.displayLabel
import app.knotwork.design.components.pipelineeditor.glyph
import app.knotwork.design.components.pipelineeditor.headerOnColor
import app.knotwork.design.components.pipelineeditor.headerTint
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

private const val MENU_RADIUS_DP = 96f
private const val TILE_SIZE_DP = 56f

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
    Box(
        modifier = modifier
            .fillMaxSize()
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
        types.forEachIndexed { index, type ->
            val theta = (TWO_PI * index / count) - HALF_PI
            val tileScreenX = screenAnchorX + radius * cos(theta).toFloat() - tilePx / 2f
            val tileScreenY = screenAnchorY + radius * sin(theta).toFloat() - tilePx / 2f
            QuickAddTile(
                type = type,
                onPick = {
                    onPick(NodeTypeMapper.toDomain(type))
                },
                modifier = Modifier
                    .graphicsLayer {
                        translationX = tileScreenX
                        translationY = tileScreenY
                    }
                    .size(TILE_SIZE_DP.dp),
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
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onPick),
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
                contentDescription = type.displayLabel(),
                tint = onTint,
                modifier = Modifier.size(GLYPH_SIZE_DP.dp),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.5f
private const val TWO_PI = 2.0 * PI
private const val HALF_PI = PI / 2.0
private const val GLYPH_SIZE_DP = 20f
