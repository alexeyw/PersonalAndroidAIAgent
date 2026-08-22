package app.knotwork.design.screens.settings

/**
 * One configurable limit on the run-limits screen.
 *
 * Every string arrives resolved and every number pre-formatted, so this module
 * holds no opinion about how a limit is worded or how a large number is
 * grouped — the host owns both, in one place.
 *
 * @property label Row title.
 * @property valueLabel The current value, already formatted for display.
 * @property description What this limit governs, in a sentence. Not optional:
 *   before this row existed, the only explanation of a limit anywhere in the
 *   app lived in the settings-search index, where nobody looked at it and it
 *   quietly went false.
 * @property qualifier A word explaining the value's *state* — today, that it is
 *   inherited rather than independently set — or `null` when the value stands
 *   on its own.
 * @property value Current slider position.
 * @property valueRange Inclusive bounds of the slider.
 * @property minLabel Formatted lower bound, rendered under the track.
 * @property maxLabel Formatted upper bound.
 */
data class LimitSliderRowState(
    val label: String,
    val valueLabel: String,
    val description: String,
    val qualifier: String? = null,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val minLabel: String,
    val maxLabel: String,
)

/**
 * An axis the product states rather than controls.
 *
 * @property label What the axis is.
 * @property stateWord Its state, in one word — "Not measured".
 * @property body Why, in a sentence, and what to use instead.
 */
data class StatementRowState(val label: String, val stateWord: String, val body: String)

/**
 * Everything the run-limits screen renders.
 *
 * Four numbers and one statement. Not eight: the soft threshold at which a run
 * is warned is fixed arithmetic — 75 % of the hard limit — rather than a second
 * setting per axis, so the screen stays a screen instead of a wall.
 *
 * @property intro What these limits are and what counts towards them.
 * @property stepsGroupLabel Heading above the step limits.
 * @property steps Interactive step limit.
 * @property stepsBackground Step limit for runs nobody is watching.
 * @property tokensGroupLabel Heading above the token limits.
 * @property tokens Interactive token limit.
 * @property tokensBackground Token limit for background runs.
 * @property spendGroupLabel Heading above the spend statement.
 * @property spend The money axis, stated rather than controlled.
 * @property softNote When a run warns itself, and that the point is fixed.
 */
data class RunLimitsViewState(
    val intro: String,
    val stepsGroupLabel: String,
    val steps: LimitSliderRowState,
    val stepsBackground: LimitSliderRowState,
    val tokensGroupLabel: String,
    val tokens: LimitSliderRowState,
    val tokensBackground: LimitSliderRowState,
    val spendGroupLabel: String,
    val spend: StatementRowState,
    val softNote: String,
)

/**
 * Value-change handlers for [RunLimitsContent].
 *
 * Every axis has a *move* and a *commit*. The split exists for the background
 * rows: writing a background limit is what stops it following the interactive
 * one, permanently, so that must happen when the user finishes deciding — not
 * on every frame of a drag, and not when a touch lands and goes nowhere.
 *
 * @property onStepsChange Interactive step limit moved.
 * @property onStepsCommit Interactive step gesture finished.
 * @property onStepsBackgroundChange Background step limit moved.
 * @property onStepsBackgroundCommit Background step gesture finished.
 * @property onTokensChange Interactive token limit moved.
 * @property onTokensCommit Interactive token gesture finished.
 * @property onTokensBackgroundChange Background token limit moved.
 * @property onTokensBackgroundCommit Background token gesture finished.
 */
class RunLimitsCallbacks(
    val onStepsChange: (Float) -> Unit = {},
    val onStepsCommit: () -> Unit = {},
    val onStepsBackgroundChange: (Float) -> Unit = {},
    val onStepsBackgroundCommit: () -> Unit = {},
    val onTokensChange: (Float) -> Unit = {},
    val onTokensCommit: () -> Unit = {},
    val onTokensBackgroundChange: (Float) -> Unit = {},
    val onTokensBackgroundCommit: () -> Unit = {},
)
