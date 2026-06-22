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
 * Privacy category sub-screen. Basic tier surfaces the crash-reporting consent
 * toggle; the Advanced disclosure holds the run-trace retention sliders.
 *
 * @param state Immutable Privacy state.
 * @param modifier Layout modifier.
 * @param callbacks Interaction callbacks.
 */
@Composable
fun PrivacySettingsContent(
    state: PrivacySettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
    advancedExpanded: Boolean = false,
) {
    CategoryScaffold(
        title = stringResource(R.string.knotwork_settings_cat_privacy_title),
        subtitle = stringResource(R.string.knotwork_settings_count, 1 + state.advancedSliders.size),
        onBack = callbacks.onBack,
        modifier = modifier,
    ) {
        IconToggleRow(
            icon = AppIcons.Shield,
            title = stringResource(R.string.knotwork_settings_crash_reporting_label),
            subtitle = stringResource(R.string.knotwork_settings_crash_reporting_hint),
            checked = state.crashReportingEnabled,
            onCheckedChange = callbacks.onCrashReportingToggle,
        )
        AdvancedDisclosure(initiallyExpanded = advancedExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                SettingSliderList(
                    sliders = state.advancedSliders,
                    tagPrefix = PRIVACY_PARAM_ROW_TAG_PREFIX,
                    onChange = callbacks.onPrivacySliderChange,
                )
            }
        }
    }
}
