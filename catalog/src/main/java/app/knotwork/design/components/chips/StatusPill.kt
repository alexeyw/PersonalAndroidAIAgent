package app.knotwork.design.components.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Pill visual height. */
private val StatusPillHeight = 22.dp

/** Diameter of the leading status dot. */
private val StatusDotSize = 6.dp

/**
 * Pipeline / connection run status. Maps to the `extended.signal*` palette
 * (plus a neutral tone for `Idle`).
 */
enum class Status {
    /** No run yet. Neutral surface tone. */
    Idle,

    /** Run in progress. Same accent as the brand primary. */
    Running,

    /** Run completed successfully. Hue-locked `signalSuccess`. */
    Success,

    /** Run finished with non-fatal warnings. Hue-locked `signalWarn`. */
    Warning,

    /** Run failed. Hue-locked `signalError`. */
    Error,
}

/**
 * Knotwork status pill — 6 dp dot + label, pairing colour with text so the
 * state is legible without colour vision (`decisions.md §14`).
 *
 * Visual contract (see `compose/components/README.md` §Chips & pills):
 *  - 22 dp tall, shape `KnotworkTheme.shapes.full`, container `extended.surface2`,
 *    label `LabelSm` in `extended.onSurface2`.
 *  - 6 dp dot pulled from the `signal*` palette (or neutral for [Status.Idle]).
 *  - `contentDescription` reads `"Status: <label>"` so the pill is announced
 *    as a state, not as decoration.
 *
 * @param status status driving the dot colour and label.
 * @param modifier optional layout modifier applied to the pill root.
 */
@Composable
fun StatusPill(status: Status, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = modifier
            .height(StatusPillHeight)
            .background(color = KnotworkTheme.extended.surface2, shape = KnotworkTheme.shapes.full)
            .padding(horizontal = KnotworkTheme.spacing.sp2)
            .semantics { contentDescription = "Status: ${status.label}" },
    ) {
        Box(
            modifier = Modifier
                .size(StatusDotSize)
                .background(color = statusDotColor(status), shape = CircleShape),
        )
        Text(
            text = status.label,
            style = KnotworkTextStyles.LabelSm,
            color = KnotworkTheme.extended.onSurface2,
        )
    }
}

/** Maps a [Status] to the dot colour. */
@Composable
private fun statusDotColor(status: Status): Color = when (status) {
    Status.Idle -> KnotworkTheme.extended.outlineStrong
    Status.Running -> androidx.compose.material3.MaterialTheme.colorScheme.primary
    Status.Success -> KnotworkTheme.extended.signalSuccess
    Status.Warning -> KnotworkTheme.extended.signalWarn
    Status.Error -> KnotworkTheme.extended.signalError
}

/** Human-readable label rendered inside the pill (also used in `contentDescription`). */
private val Status.label: String
    get() = when (this) {
        Status.Idle -> "Idle"
        Status.Running -> "Running"
        Status.Success -> "Success"
        Status.Warning -> "Warning"
        Status.Error -> "Error"
    }
