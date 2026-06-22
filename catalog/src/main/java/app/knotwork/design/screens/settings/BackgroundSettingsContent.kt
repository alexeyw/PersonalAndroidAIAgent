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
        subtitle = stringResource(R.string.knotwork_settings_count, 2 + state.advancedSliders.size),
        onBack = callbacks.onBack,
        modifier = modifier,
    ) {
        SettingsAnchor(anchorKey = "LONG_RUNNING_TASKS_NOTIFICATIONS") {
            IconToggleRow(
                icon = AppIcons.Bolt,
                title = stringResource(R.string.knotwork_settings_notifications_long_running),
                subtitle = stringResource(R.string.knotwork_settings_notifications_long_running_subtitle),
                checked = state.longRunningEnabled,
                onCheckedChange = callbacks.onLongRunningToggle,
            )
        }
        SettingsAnchor(anchorKey = "SCHEDULED_TASK_NOTIFICATIONS") {
            IconToggleRow(
                icon = AppIcons.History,
                title = stringResource(R.string.knotwork_settings_notifications_scheduled_results),
                subtitle = stringResource(R.string.knotwork_settings_notifications_scheduled_results_subtitle),
                checked = state.scheduledResultsEnabled,
                onCheckedChange = callbacks.onScheduledResultsToggle,
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
