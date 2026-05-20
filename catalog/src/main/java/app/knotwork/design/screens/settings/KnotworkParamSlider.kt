package app.knotwork.design.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Branded labelled slider used for every numeric parameter row inside
 * `SettingsContent` (temperature, top-K, top-P, max context, max steps,
 * memory-summary default limit).
 *
 * Visual contract:
 *  - `TitleMd` label on the left, brand-primary mono value label on the
 *    right edge (mirrors the spec mockup).
 *  - Material `Slider` tinted via `MaterialTheme.colorScheme.primary` on
 *    the active track / thumb / ticks and `KnotworkTheme.extended.surface3`
 *    on the inactive track.
 *
 * The composable is stateless: callers provide both the current [value]
 * and the [valueLabel] string (so the row can pluralise or format the
 * displayed number).
 *
 * @param label row title rendered in `TitleMd`.
 * @param valueLabel pre-formatted current value (e.g. `"0.7"`, `"4096 tok"`).
 * @param value current slider position.
 * @param onValueChange callback invoked while the user drags.
 * @param valueRange inclusive value range.
 * @param modifier optional modifier applied to the wrapping `Column`.
 * @param steps number of discrete intermediate steps; `0` (default) keeps
 *  the slider continuous.
 * @param enabled `false` puts the slider in a read-only state during a
 *  `PendingChange` transition.
 */
@Suppress("LongParameterList") // Stable public API; each parameter maps to a row attribute.
@Composable
fun KnotworkParamSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = KnotworkTextStyles.MonoBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = KnotworkTheme.extended.surface3,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = KnotworkTheme.extended.surface3,
            ),
        )
    }
}
