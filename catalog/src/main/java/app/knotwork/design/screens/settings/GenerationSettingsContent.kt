package app.knotwork.design.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.theme.KnotworkTheme

/**
 * Generation category sub-screen. Basic tier surfaces the system-instructions
 * textarea; the Advanced disclosure holds the sampling sliders, the
 * voice-input length and the reset-sampling action.
 *
 * @param state Immutable Generation state.
 * @param modifier Layout modifier.
 * @param callbacks Interaction callbacks.
 * @param advancedExpanded Seeds the Advanced disclosure open (snapshot fixtures).
 */
@Composable
fun GenerationSettingsContent(
    state: GenerationSettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
    advancedExpanded: Boolean = false,
) {
    val count = 1 + state.advancedSliders.size
    CategoryScaffold(
        title = stringResource(R.string.knotwork_settings_cat_generation_title),
        subtitle = stringResource(R.string.knotwork_settings_count, count),
        onBack = callbacks.onBack,
        modifier = modifier,
    ) {
        SettingsAnchor(anchorKey = SettingsRowAnchors.SYSTEM_PROMPT_PREFIX) {
            SystemInstructionsField(
                state = state.systemInstructions,
                onValueChange = callbacks.onSystemInstructionsChange,
                onChipInsert = callbacks.onChipInsert,
            )
        }
        AdvancedDisclosure(initiallyExpanded = advancedExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                SettingSliderList(
                    sliders = state.advancedSliders,
                    tagPrefix = GENERATION_SLIDER_TAG_PREFIX,
                    onChange = callbacks.onGenerationSliderChange,
                )
                KnotworkTextButton(
                    text = stringResource(R.string.knotwork_settings_llm_reset),
                    onClick = callbacks.onResetSamplingDefaults,
                )
            }
        }
    }
}
