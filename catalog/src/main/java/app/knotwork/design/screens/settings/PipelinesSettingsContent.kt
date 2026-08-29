package app.knotwork.design.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.R
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme

/**
 * Pipelines-&-structured-output category sub-screen. Basic tier links out to
 * the run-limits screen, carrying the current limits as its subtitle; the
 * Advanced disclosure holds the max-nesting-depth and structured-output
 * repair-budget sliders.
 *
 * @param state Immutable Pipelines state.
 * @param modifier Layout modifier.
 * @param callbacks Interaction callbacks.
 */
@Composable
fun PipelinesSettingsContent(
    state: PipelinesSettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
    advancedExpanded: Boolean = false,
) {
    CategoryScaffold(
        title = stringResource(R.string.knotwork_settings_cat_pipelines_title),
        subtitle = stringResource(R.string.knotwork_settings_count, 1 + state.advancedSliders.size),
        onBack = callbacks.onBack,
        modifier = modifier,
    ) {
        SettingsAnchor(anchorKey = SettingsRowAnchors.LINK_RUN_LIMITS) {
            NavLinkRow(
                icon = AppIcons.Shield,
                title = stringResource(R.string.knotwork_settings_run_limits_title),
                state = state.runLimitsSummary,
                onClick = callbacks.onOpenRunLimits,
            )
        }
        AdvancedDisclosure(initiallyExpanded = advancedExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                SettingSliderList(
                    sliders = state.advancedSliders,
                    tagPrefix = PIPELINE_SLIDER_TAG_PREFIX,
                    onChange = callbacks.onPipelinesSliderChange,
                )
            }
        }
    }
}
