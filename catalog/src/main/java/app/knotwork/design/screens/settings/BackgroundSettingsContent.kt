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
 * Background-&-triggers category sub-screen. Basic tier surfaces the two
 * notification toggles; the Advanced disclosure holds the resume-window and
 * background-approval-window sliders.
 *
 * @param state Immutable Background state.
 * @param modifier Layout modifier.
 * @param callbacks Interaction callbacks.
 */
@Composable
fun BackgroundSettingsContent(
    state: BackgroundSettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
    advancedExpanded: Boolean = false,
) {
    CategoryScaffold(
        title = stringResource(R.string.knotwork_settings_cat_background_title),
        subtitle = stringResource(R.string.knotwork_settings_count, BASIC_ROW_COUNT + state.advancedSliders.size),
        onBack = callbacks.onBack,
        modifier = modifier,
    ) {
        SettingsAnchor(anchorKey = SettingsRowAnchors.LONG_RUNNING_TASKS_NOTIFICATIONS) {
            IconToggleRow(
                icon = AppIcons.Bolt,
                title = stringResource(R.string.knotwork_settings_notifications_long_running),
                subtitle = stringResource(R.string.knotwork_settings_notifications_long_running_subtitle),
                checked = state.longRunningEnabled,
                onCheckedChange = callbacks.onLongRunningToggle,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.SCHEDULED_TASK_NOTIFICATIONS) {
            IconToggleRow(
                icon = AppIcons.History,
                title = stringResource(R.string.knotwork_settings_notifications_scheduled_results),
                subtitle = stringResource(R.string.knotwork_settings_notifications_scheduled_results_subtitle),
                checked = state.scheduledResultsEnabled,
                onCheckedChange = callbacks.onScheduledResultsToggle,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.SHARE_TARGET_PIPELINE_ID) {
            NavLinkRow(
                icon = AppIcons.Share,
                title = stringResource(R.string.knotwork_settings_share_pipeline_title),
                subtitle = state.shareTargetPipelineLabel,
                onClick = callbacks.onShareTargetPipelineClick,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.QUICK_SETTINGS_TILE_PIPELINE_ID) {
            NavLinkRow(
                icon = AppIcons.Bolt,
                title = stringResource(R.string.knotwork_settings_tile_pipeline_title),
                subtitle = state.quickTilePipelineLabel,
                onClick = callbacks.onQuickTilePipelineClick,
            )
        }
        AdvancedDisclosure(initiallyExpanded = advancedExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                SettingSliderList(
                    sliders = state.advancedSliders,
                    tagPrefix = BACKGROUND_SLIDER_TAG_PREFIX,
                    onChange = callbacks.onBackgroundSliderChange,
                )
            }
        }
    }
}

/** Number of Basic-tier rows on the Background sub-screen (two toggles + two pipeline bindings). */
private const val BASIC_ROW_COUNT = 4
