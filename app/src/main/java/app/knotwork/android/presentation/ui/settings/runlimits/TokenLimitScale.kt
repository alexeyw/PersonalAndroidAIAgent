package app.knotwork.android.presentation.ui.settings.runlimits

import app.knotwork.android.domain.constants.SettingsDefaults
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Slider scale for the token ceilings.
 *
 * The token range spans three orders of magnitude — 10 000 to 10 000 000 — and
 * on a linear track that is unusable: every value a person would actually pick
 * is crushed into the first few percent, and one pixel of travel near the right
 * end moves the limit by tens of thousands of tokens. So the track is
 * logarithmic, which makes a given *proportional* change cost the same drag
 * distance anywhere on it.
 *
 * The step ceilings keep a linear track: 5 to 100 is one order of magnitude and
 * reads naturally.
 */
internal object TokenLimitScale {

    /** Slider position of the lowest permitted token ceiling. */
    val minPosition: Float = log10(SettingsDefaults.RUN_MAX_TOKENS_MIN.toDouble()).toFloat()

    /** Slider position of the highest permitted token ceiling. */
    val maxPosition: Float = log10(SettingsDefaults.RUN_MAX_TOKENS_MAX.toDouble()).toFloat()

    /**
     * Converts a token ceiling to its position on the track.
     *
     * @param tokens The ceiling.
     * @return The slider position.
     */
    fun positionOf(tokens: Int): Float =
        log10(tokens.coerceIn(SettingsDefaults.RUN_MAX_TOKENS_MIN, SettingsDefaults.RUN_MAX_TOKENS_MAX).toDouble())
            .toFloat()

    /**
     * Converts a track position back to a token ceiling.
     *
     * Rounded to three significant figures, because the exact output of an
     * exponential is meaningless as a *limit*: nobody means "1 037 528 tokens",
     * they mean "about a million", and a settings row that reads back the raw
     * value looks broken rather than precise.
     *
     * @param position The slider position.
     * @return A ceiling inside the permitted range.
     */
    fun tokensAt(position: Float): Int {
        val raw = 10.0.pow(position.toDouble())
        val magnitude = 10.0.pow(log10(raw).toInt() - SIGNIFICANT_FIGURES + 1).coerceAtLeast(1.0)
        val rounded = (raw / magnitude).roundToLong() * magnitude
        return rounded.roundToInt()
            .coerceIn(SettingsDefaults.RUN_MAX_TOKENS_MIN, SettingsDefaults.RUN_MAX_TOKENS_MAX)
    }

    /** Digits kept when reading a value off the logarithmic track. */
    private const val SIGNIFICANT_FIGURES: Int = 3
}
