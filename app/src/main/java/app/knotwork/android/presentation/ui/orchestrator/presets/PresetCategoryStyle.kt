package app.knotwork.android.presentation.ui.orchestrator.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.models.PresetCategory

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
