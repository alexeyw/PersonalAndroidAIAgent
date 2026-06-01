package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkButtonSize
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Pipeline-run status banner — the prominent strip the designer puts above the
 * canvas to communicate run progress at a glance.
 *
 * Mockup reference (designer-supplied 2026-05-23):
 *  - **Done:** green left dot + `DONE` label + per-run metrics (`11 / 11 · 12.8
 *    s · 2 408 tok`) + trailing `Trace` action.
 *  - **Running:** amber left dot + `RUNNING` label + live counter (`step 6 / 11
 *    · 4.2 s`) + trailing `Pause` and `Stop` (destructive) actions.
 *
 * The catalog atom owns the visual contract; the production layer
 * (`PipelineEditorScreen`) supplies the data (`status`) and the action
 * callbacks. When [status] is [RunStatus.Idle] the banner renders nothing — the
 * caller can splice it into a layout unconditionally.
 *
 * @param status current run status — drives variant, label, metrics, and which
 *   actions render.
 * @param onTrace invoked when the trailing `Trace` button is tapped (Done only).
 * @param onPause invoked when the trailing `Pause` button is tapped (Running).
 * @param onResume invoked when the trailing `Resume` button is tapped (Paused).
 * @param onStop invoked when the trailing destructive `Stop` button is tapped
 *   (Running or Paused). Caller is responsible for confirmation if needed —
 *   the button does not gate.
 * @param modifier optional layout modifier applied to the banner root.
 */
@Composable
@Suppress("LongMethod") // The variants share the same layout — splitting would scatter the visual contract.
fun RunStatusBanner(
    status: RunStatus,
    modifier: Modifier = Modifier,
    onTrace: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    if (status is RunStatus.Idle) return
    val accent = accentFor(status)
    val background = backgroundTintFor(accent)
    // Single Surface owns BOTH the background tint AND the accent border so the
    // two are always the same shape and size. Wrapping the content in a second
    // Surface (the prior layout) made the border anchor to a fixed-min-height
    // box that didn't grow with the row content — the border visibly framed a
    // smaller area than the background tint. Material3 `Surface` accepts a
    // `border` parameter directly; use it.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = BannerMinHeight),
        color = background,
        shape = KnotworkTheme.shapes.md,
        border = BorderStroke(width = 1.dp, color = accent.copy(alpha = BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BannerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        ) {
            StatusBadge(accent = accent, label = labelFor(status))
            MetricsRow(status = status, modifier = Modifier.weight(1f))
            ActionsRow(
                status = status,
                onTrace = onTrace,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
            )
        }
    }
}

/**
 * Run status communicated by the banner. The production layer maps its
 * `runState` (plus a screen-local clock for elapsed time) onto this sealed type.
 *
 * - [Idle] — no run yet. Banner renders nothing.
 * - [Running] — pipeline is executing. Shows step counter and elapsed time;
 *   exposes Pause / Stop.
 * - [Paused] — user paused the run. Same metrics; exposes Resume / Stop.
 * - [Done] — last run completed successfully. Shows total steps, duration, and
 *   (optional) token count; exposes Trace.
 */
sealed interface RunStatus {
    /** No active or recent run — banner is hidden. */
    data object Idle : RunStatus

    /**
     * Live run snapshot.
     *
     * @property stepIndex 1-based index of the currently-executing node.
     * @property totalSteps total nodes (or `null` if the engine has not declared a budget).
     * @property elapsedSeconds wall-clock seconds since the run started.
     */
    data class Running(val stepIndex: Int, val totalSteps: Int?, val elapsedSeconds: Float) : RunStatus

    /**
     * Paused run snapshot. Same shape as [Running]; the banner swaps Pause for
     * Resume but otherwise renders identically.
     */
    data class Paused(val stepIndex: Int, val totalSteps: Int?, val elapsedSeconds: Float) : RunStatus

    /**
     * Last successful run snapshot.
     *
     * @property totalSteps total nodes executed.
     * @property elapsedSeconds wall-clock seconds the run took.
     * @property tokens total tokens consumed by LLM nodes; `null` when no LLM
     *   node ran or telemetry is unavailable.
     */
    data class Done(val totalSteps: Int, val elapsedSeconds: Float, val tokens: Int?) : RunStatus
}

private val BannerMinHeight = 56.dp
private val BannerPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
private const val BORDER_ALPHA = 0.45f
private const val BACKGROUND_ALPHA = 0.12f

@Composable
private fun accentFor(status: RunStatus): Color = when (status) {
    RunStatus.Idle -> Color.Transparent
    is RunStatus.Running -> KnotworkTheme.extended.signalWarn
    is RunStatus.Paused -> KnotworkTheme.extended.signalWarn
    is RunStatus.Done -> KnotworkTheme.extended.signalSuccess
}

@Composable
private fun backgroundTintFor(accent: Color): Color = accent.copy(alpha = BACKGROUND_ALPHA)

@Composable
private fun labelFor(status: RunStatus): String = when (status) {
    RunStatus.Idle -> ""
    is RunStatus.Running -> stringResource(R.string.knotwork_run_banner_label_running)
    is RunStatus.Paused -> stringResource(R.string.knotwork_run_banner_label_paused)
    is RunStatus.Done -> stringResource(R.string.knotwork_run_banner_label_done)
}

/** Left dot + uppercase status label. Mirrors the mockup `● RUNNING` / `● DONE` group. */
@Composable
private fun StatusBadge(accent: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = accent, shape = CircleShape),
        )
        Text(
            text = label,
            style = KnotworkTextStyles.LabelSm.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = KnotworkTheme.extended.onSurface2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dot-separated metric line. `· stepIndex / totalSteps · elapsed s · tokens tok`. */
@Composable
private fun MetricsRow(status: RunStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        RunStatus.Idle -> ""
        is RunStatus.Running -> runningMetrics(status.stepIndex, status.totalSteps, status.elapsedSeconds)
        is RunStatus.Paused -> runningMetrics(status.stepIndex, status.totalSteps, status.elapsedSeconds)
        is RunStatus.Done -> doneMetrics(status.totalSteps, status.elapsedSeconds, status.tokens)
    }
    Text(
        text = text,
        style = KnotworkTextStyles.BodySm,
        color = KnotworkTheme.extended.onSurfaceMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun runningMetrics(stepIndex: Int, totalSteps: Int?, elapsedSeconds: Float): String {
    val stepLabel = if (totalSteps != null) {
        stringResource(R.string.knotwork_run_banner_metric_step_of, stepIndex, totalSteps)
    } else {
        stringResource(R.string.knotwork_run_banner_metric_step_only, stepIndex)
    }
    val elapsedLabel = stringResource(R.string.knotwork_run_banner_metric_elapsed, formatSeconds(elapsedSeconds))
    return "· $stepLabel · $elapsedLabel"
}

@Composable
private fun doneMetrics(totalSteps: Int, elapsedSeconds: Float, tokens: Int?): String {
    val stepLabel = stringResource(R.string.knotwork_run_banner_metric_total_steps, totalSteps, totalSteps)
    val elapsedLabel = stringResource(R.string.knotwork_run_banner_metric_elapsed, formatSeconds(elapsedSeconds))
    val tokenLabel = tokens?.let {
        stringResource(R.string.knotwork_run_banner_metric_tokens, formatTokens(it))
    }
    return buildString {
        append("· ")
        append(stepLabel)
        append(" · ")
        append(elapsedLabel)
        if (tokenLabel != null) {
            append(" · ")
            append(tokenLabel)
        }
    }
}

@Composable
private fun ActionsRow(
    status: RunStatus,
    onTrace: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        when (status) {
            RunStatus.Idle -> Unit
            is RunStatus.Running -> {
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_run_banner_action_pause),
                    onClick = onPause,
                    size = KnotworkButtonSize.Sm,
                    leadingIcon = AppIcons.Pause,
                )
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_run_banner_action_stop),
                    onClick = onStop,
                    size = KnotworkButtonSize.Sm,
                    leadingIcon = AppIcons.Stop,
                    destructive = true,
                )
            }
            is RunStatus.Paused -> {
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_run_banner_action_resume),
                    onClick = onResume,
                    size = KnotworkButtonSize.Sm,
                    leadingIcon = AppIcons.Play,
                )
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_run_banner_action_stop),
                    onClick = onStop,
                    size = KnotworkButtonSize.Sm,
                    leadingIcon = AppIcons.Stop,
                    destructive = true,
                )
            }
            is RunStatus.Done -> {
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_run_banner_action_trace),
                    onClick = onTrace,
                    size = KnotworkButtonSize.Sm,
                    leadingIcon = AppIcons.History,
                )
            }
        }
    }
}

/** Formats elapsed seconds as `"4.2"` (1 decimal) to match the mockup wording. */
private fun formatSeconds(seconds: Float): String {
    // Use Locale-stable formatting rounded (not truncated) to 1 decimal — banner
    // copy is "4.2 s", not a precise telemetry readout. `kotlin.math.round` is
    // banker-free half-up rounding; `(x * 10).toInt()` alone would floor `4.29`
    // to `4.2` instead of rounding to `4.3`.
    val roundedTenths = kotlin.math.round(seconds * DECIMAL_FACTOR).toInt()
    val intPart = roundedTenths / DECIMAL_FACTOR.toInt()
    val decPart = kotlin.math.abs(roundedTenths % DECIMAL_FACTOR.toInt()).coerceIn(0, DECIMAL_MAX)
    return "$intPart.$decPart"
}

/** Formats token counts with a thin-space thousands grouping (matches mockup `2 408 tok`). */
private fun formatTokens(tokens: Int): String {
    val abs = kotlin.math.abs(tokens)
    if (abs < THOUSANDS_GROUP) return tokens.toString()
    val sign = if (tokens < 0) "-" else ""
    val s = abs.toString()
    val sb = StringBuilder()
    val rem = s.length % GROUP_DIGITS
    s.forEachIndexed { index, c ->
        // U+2009 THIN SPACE — matches the designer mockup's typographic
        // thousands separator. A regular space (U+0020) would render visibly
        // wider on most fonts and break the compact `2 408 tok` look.
        if (index != 0 && (index - rem) % GROUP_DIGITS == 0) sb.append(THIN_SPACE)
        sb.append(c)
    }
    return sign + sb.toString()
}

private const val DECIMAL_FACTOR = 10f
private const val DECIMAL_MAX = 9
private const val THOUSANDS_GROUP = 1_000
private const val GROUP_DIGITS = 3

/** Unicode U+2009 — the narrow space used by the designer for thousands grouping. */
private const val THIN_SPACE: Char = ' '

@Preview(name = "RunStatusBanner — running (light)", widthDp = 720, showBackground = true)
@Composable
private fun RunStatusBannerRunningLightPreview() {
    KnotworkTheme(darkTheme = false) {
        RunStatusBanner(
            status = RunStatus.Running(stepIndex = 6, totalSteps = 11, elapsedSeconds = 4.2f),
        )
    }
}

@Preview(name = "RunStatusBanner — done (light)", widthDp = 720, showBackground = true)
@Composable
private fun RunStatusBannerDoneLightPreview() {
    KnotworkTheme(darkTheme = false) {
        RunStatusBanner(
            status = RunStatus.Done(totalSteps = 11, elapsedSeconds = 12.8f, tokens = 2408),
        )
    }
}

@Preview(name = "RunStatusBanner — paused (dark)", widthDp = 720, showBackground = true)
@Composable
private fun RunStatusBannerPausedDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        RunStatusBanner(
            status = RunStatus.Paused(stepIndex = 3, totalSteps = 11, elapsedSeconds = 1.8f),
        )
    }
}
