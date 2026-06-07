package app.knotwork.android.presentation.ui.orchestrator.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.android.R
import app.knotwork.android.domain.models.PresetCategory
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Maps a [PresetCategory] to its accent color, sourced from the Knotwork
 * extended palette so the picker / manager rows stay on-brand and theme-aware.
 *
 * The mapping deliberately reuses node hues (`nodeCloud`, `nodeTool`,
 * `nodeDecomposition`) and signal hues (`signalSuccess`, `signalWarn`) — both
 * sets are hue-locked across light / dark themes, so the category chips stay
 * legible in either palette.
 */
@Composable
internal fun presetCategoryColor(category: PresetCategory): Color = when (category) {
    PresetCategory.LOCAL -> KnotworkTheme.extended.signalSuccess
    PresetCategory.CLOUD -> KnotworkTheme.extended.nodeCloud
    PresetCategory.HYBRID -> KnotworkTheme.extended.signalWarn
    PresetCategory.TOOL -> KnotworkTheme.extended.nodeTool
    PresetCategory.RESEARCH -> KnotworkTheme.extended.nodeDecomposition
    PresetCategory.OTHER -> KnotworkTheme.extended.onSurfaceMuted
}

/**
 * Maps a [PresetCategory] to its display label resource. Pulled out so the
 * tab / chip / card surfaces all read from the same source.
 */
@Composable
internal fun presetCategoryLabelText(category: PresetCategory): String = when (category) {
    PresetCategory.LOCAL -> stringResource(R.string.orchestrator_preset_category_local)
    PresetCategory.CLOUD -> stringResource(R.string.orchestrator_preset_category_cloud)
    PresetCategory.HYBRID -> stringResource(R.string.orchestrator_preset_category_hybrid)
    PresetCategory.TOOL -> stringResource(R.string.orchestrator_preset_category_tool)
    PresetCategory.RESEARCH -> stringResource(R.string.orchestrator_preset_category_research)
    PresetCategory.OTHER -> stringResource(R.string.orchestrator_preset_category_other)
}

/**
 * Pill-shaped chip rendered on each preset card to communicate the preset's
 * category. Visual:
 *
 *  - 1 dp coloured border (no fill) with the category hue.
 *  - Leading 6 dp coloured dot.
 *  - Category label in `LabelSm`, colored to match the border.
 *
 * Used by [PresetPickerSheet] cards and [PipelinePresetsManagerScreen] rows
 * so the visual treatment of the category badge stays consistent across the
 * two surfaces.
 */
@Composable
internal fun PresetCategoryBadge(category: PresetCategory, modifier: Modifier = Modifier) {
    val tint = presetCategoryColor(category)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = modifier
            .clip(RoundedCornerShape(percent = BADGE_CORNER_PERCENT))
            .border(width = 1.dp, color = tint, shape = RoundedCornerShape(percent = BADGE_CORNER_PERCENT))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = BADGE_VERTICAL_PADDING),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(BADGE_DOT_SIZE)
                .clip(RoundedCornerShape(percent = ROUND_PILL_PERCENT))
                .background(tint),
        )
        Text(
            text = presetCategoryLabelText(category),
            style = KnotworkTextStyles.LabelSm,
            color = tint,
        )
    }
}

private const val BADGE_CORNER_PERCENT = 50
private const val ROUND_PILL_PERCENT = 50
private val BADGE_DOT_SIZE = 6.dp
private val BADGE_VERTICAL_PADDING = 2.dp
