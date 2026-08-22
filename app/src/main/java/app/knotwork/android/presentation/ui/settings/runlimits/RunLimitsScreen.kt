package app.knotwork.android.presentation.ui.settings.runlimits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.android.R
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.design.screens.settings.LimitSliderRowState
import app.knotwork.design.screens.settings.RunLimitsCallbacks
import app.knotwork.design.screens.settings.RunLimitsContent
import app.knotwork.design.screens.settings.RunLimitsViewState
import app.knotwork.design.screens.settings.StatementRowState
import kotlin.math.roundToInt

/**
 * The run-limits screen: every ceiling one autonomous run is held to.
 *
 * Reached from the Pipelines category entry row, which carries the current
 * limits as its subtitle. The screen exists because the ceilings shipped
 * before any way to read them — three of the four numbers the engine enforces
 * had no surface at all, and the fourth was described by a settings-search
 * string promising a pause the engine never performs.
 *
 * @param viewModel Owner of the four values.
 * @param onBack Invoked by the scaffold's back affordance.
 * @param modifier Optional layout modifier.
 */
@Composable
fun RunLimitsScreen(viewModel: RunLimitsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    RunLimitsContent(
        state = buildRunLimitsViewState(uiState),
        callbacks = RunLimitsCallbacks(
            onStepsChange = { viewModel.onStepsChange(it.roundToInt()) },
            onStepsCommit = viewModel::onStepsCommit,
            onStepsBackgroundChange = { viewModel.onStepsBackgroundChange(it.roundToInt()) },
            onStepsBackgroundCommit = viewModel::onStepsBackgroundCommit,
            onTokensChange = { viewModel.onTokensChange(TokenLimitScale.tokensAt(it)) },
            onTokensCommit = viewModel::onTokensCommit,
            onTokensBackgroundChange = { viewModel.onTokensBackgroundChange(TokenLimitScale.tokensAt(it)) },
            onTokensBackgroundCommit = viewModel::onTokensBackgroundCommit,
        ),
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Projects the four persisted numbers onto the catalog view state, resolving
 * every string and formatting every figure.
 *
 * @param uiState The current values and the inherited flag.
 * @return The state the design module renders.
 */
@Composable
internal fun buildRunLimitsViewState(uiState: RunLimitsUiState): RunLimitsViewState = RunLimitsViewState(
    intro = stringResource(R.string.run_limits_intro),
    stepsGroupLabel = stringResource(R.string.run_limits_group_steps),
    steps = LimitSliderRowState(
        label = stringResource(R.string.run_limits_steps_label),
        valueLabel = number(uiState.steps),
        description = stringResource(R.string.run_limits_steps_desc),
        value = uiState.steps.toFloat(),
        valueRange = SettingsDefaults.PIPELINE_MAX_STEPS_MIN.toFloat()
            .rangeTo(SettingsDefaults.PIPELINE_MAX_STEPS_MAX.toFloat()),
        minLabel = number(SettingsDefaults.PIPELINE_MAX_STEPS_MIN),
        maxLabel = number(SettingsDefaults.PIPELINE_MAX_STEPS_MAX),
    ),
    stepsBackground = LimitSliderRowState(
        label = stringResource(R.string.run_limits_steps_background_label),
        valueLabel = number(uiState.stepsBackground),
        // The two descriptions say different true things. While the value is
        // inherited the important fact is that it moves with the row above;
        // once it stands alone the important fact is which runs it governs.
        description = if (uiState.stepsBackgroundInherited) {
            stringResource(R.string.run_limits_steps_background_desc_inherited)
        } else {
            stringResource(R.string.run_limits_steps_background_desc_set)
        },
        qualifier = stringResource(R.string.run_limits_qualifier_inherited)
            .takeIf { uiState.stepsBackgroundInherited },
        value = uiState.stepsBackground.toFloat(),
        valueRange = SettingsDefaults.PIPELINE_MAX_STEPS_MIN.toFloat()
            .rangeTo(SettingsDefaults.PIPELINE_MAX_STEPS_MAX.toFloat()),
        minLabel = number(SettingsDefaults.PIPELINE_MAX_STEPS_MIN),
        maxLabel = number(SettingsDefaults.PIPELINE_MAX_STEPS_MAX),
    ),
    tokensGroupLabel = stringResource(R.string.run_limits_group_tokens),
    tokens = LimitSliderRowState(
        label = stringResource(R.string.run_limits_tokens_label),
        valueLabel = number(uiState.tokens),
        description = stringResource(R.string.run_limits_tokens_desc),
        value = TokenLimitScale.positionOf(uiState.tokens),
        valueRange = TokenLimitScale.minPosition.rangeTo(TokenLimitScale.maxPosition),
        minLabel = number(SettingsDefaults.RUN_MAX_TOKENS_MIN),
        maxLabel = number(SettingsDefaults.RUN_MAX_TOKENS_MAX),
    ),
    tokensBackground = LimitSliderRowState(
        label = stringResource(R.string.run_limits_tokens_background_label),
        valueLabel = number(uiState.tokensBackground),
        // No inherited variant here, and that is not an omission: the token
        // axis is new, so there is no earlier value a background run could be
        // following. Its default is simply lower.
        description = stringResource(R.string.run_limits_tokens_background_desc),
        value = TokenLimitScale.positionOf(uiState.tokensBackground),
        valueRange = TokenLimitScale.minPosition.rangeTo(TokenLimitScale.maxPosition),
        minLabel = number(SettingsDefaults.RUN_MAX_TOKENS_MIN),
        maxLabel = number(SettingsDefaults.RUN_MAX_TOKENS_MAX),
    ),
    spendGroupLabel = stringResource(R.string.run_limits_group_spend),
    spend = StatementRowState(
        label = stringResource(R.string.run_limits_spend_label),
        stateWord = stringResource(R.string.run_limits_spend_state),
        body = stringResource(R.string.run_limits_spend_body),
    ),
    softNote = stringResource(R.string.run_limits_soft_note),
)

/**
 * Formats a limit with the reader's own digit grouping.
 *
 * Through a resource rather than `String.format` so the grouping follows the
 * device locale the same way every other number in the app does.
 *
 * @param value The figure to render.
 * @return The grouped figure.
 */
@Composable
private fun number(value: Int): String = stringResource(R.string.run_limits_number, value)
