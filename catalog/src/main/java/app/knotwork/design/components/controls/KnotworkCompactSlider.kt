package app.knotwork.design.components.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

/**
 * Brand-standard compact slider — the only slider control the app should
 * use. Renders a thin 4 dp track with a 4 × 18 dp pill-shaped thumb so the
 * slider never visually dominates the row it sits in (a regular Material
 * `Slider` ate ~48 dp vertically, which inflated every settings card and
 * every NodeConfigSheet row).
 *
 * Colour palette mirrors `KnotworkParamSlider` on Settings:
 * primary thumb + active track, `extended.surface3` inactive. Both rails
 * stay flat (no ticks, no stop indicators) regardless of [steps] so the
 * control reads the same whether driving a continuous parameter or a
 * 5-step retry count.
 *
 * Stateless: every parameter mirrors the M3 [Slider] contract.
 *
 * @param value current slider position.
 * @param onValueChange invoked while the user drags.
 * @param valueRange inclusive value range.
 * @param modifier optional layout modifier (typically `fillMaxWidth()`).
 * @param steps number of discrete intermediate steps. `0` (default) keeps
 *   the slider continuous; positive values quantise; see [Slider] caveats
 *   about high step counts blowing up tick allocation.
 * @param enabled `false` puts the slider in a read-only state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnotworkCompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    colors: SliderColors = compactSliderColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        interactionSource = interactionSource,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                modifier = Modifier.size(width = THUMB_WIDTH, height = THUMB_HEIGHT),
                thumbSize = DpSize(THUMB_WIDTH, THUMB_HEIGHT),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(TRACK_HEIGHT),
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
    )
}

/**
 * Settings-parity slider palette: primary thumb + primary active track,
 * `extended.surface3` inactive track. Lifted so callers can pass alt
 * palettes through [KnotworkCompactSlider]'s `colors` parameter when a
 * specific surface needs a different tint (rare).
 */
@Composable
fun compactSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = KnotworkTheme.extended.surface3,
    activeTickColor = MaterialTheme.colorScheme.primary,
    inactiveTickColor = KnotworkTheme.extended.surface3,
    disabledThumbColor = KnotworkTheme.extended.onSurfaceDim,
    disabledActiveTrackColor = KnotworkTheme.extended.onSurfaceDim,
    disabledInactiveTrackColor = KnotworkTheme.extended.surface3,
)

// Pill-shaped thumb tuned to the Settings reference layout — narrow enough
// not to swallow the 4 dp track but tall enough to remain hit-targetable.
private val THUMB_WIDTH = 4.dp
private val THUMB_HEIGHT = 18.dp

/** Compact track height — half the M3 default so the slider reads as a fine bar. */
private val TRACK_HEIGHT = 4.dp
