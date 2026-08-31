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
 * notification toggles, the three entry-surface bindings and the
 * external-automation consent switch with its request journal; the Advanced
 * disclosure holds the resume-window and background-approval-window sliders.
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
        SettingsAnchor(anchorKey = SettingsRowAnchors.SCHEDULED_TASK_NOTIFICATIONS) {
            IconToggleRow(
                icon = AppIcons.History,
                title = stringResource(R.string.knotwork_settings_notifications_scheduled_results),
                state = "",
                checked = state.scheduledResultsEnabled,
                onCheckedChange = callbacks.onScheduledResultsToggle,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.SHARE_TARGET_PIPELINE_ID) {
            NavLinkRow(
                icon = AppIcons.Share,
                title = stringResource(R.string.knotwork_settings_share_pipeline_title),
                state = state.shareTargetPipelineLabel,
                onClick = callbacks.onShareTargetPipelineClick,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.SHARE_REUSE_SESSION) {
            IconToggleRow(
                icon = AppIcons.Chat,
                title = stringResource(R.string.knotwork_settings_share_reuse_title),
                state = "",
                checked = state.shareReuseSessionEnabled,
                onCheckedChange = callbacks.onShareReuseSessionToggle,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.QUICK_SETTINGS_TILE_PIPELINE_ID) {
            NavLinkRow(
                icon = AppIcons.Bolt,
                title = stringResource(R.string.knotwork_settings_tile_pipeline_title),
                state = state.quickTilePipelineLabel,
                onClick = callbacks.onQuickTilePipelineClick,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.EXTERNAL_AUTOMATION_ENABLED) {
            IconToggleRow(
                icon = AppIcons.External,
                title = stringResource(R.string.knotwork_settings_external_automation_title),
                state = "",
                checked = state.externalAutomationEnabled,
                onCheckedChange = callbacks.onExternalAutomationToggle,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.EXTERNAL_AUTOMATION_PIPELINE_ID) {
            NavLinkRow(
                icon = AppIcons.Flow,
                title = stringResource(R.string.knotwork_settings_external_pipeline_title),
                state = state.externalAutomationPipelineLabel,
                onClick = callbacks.onExternalAutomationPipelineClick,
                // A switched-on surface with nothing bound accepts nothing. Left
                // as an ordinary "Not set" it reads as an untouched default; the
                // warning treatment is what tells it apart from one.
                stateWarning = state.externalAutomationUnbound,
            )
        }
        SettingsAnchor(anchorKey = SettingsRowAnchors.LINK_EXTERNAL_AUTOMATION_JOURNAL) {
            NavLinkRow(
                // Not `History`: the scheduled-task notification row above already
                // uses it, and two rows on one screen sharing a glyph makes the
                // shorter scan of the list read them as the same kind of thing.
                icon = AppIcons.Monitor,
                title = stringResource(R.string.knotwork_settings_external_journal_title),
                state = state.externalAutomationJournalLabel,
                onClick = callbacks.onOpenExternalAutomationJournal,
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

/**
 * Number of Basic-tier rows on the Background sub-screen: four toggles, three
 * pipeline bindings, and the request-journal link.
 */
private const val BASIC_ROW_COUNT = 7
