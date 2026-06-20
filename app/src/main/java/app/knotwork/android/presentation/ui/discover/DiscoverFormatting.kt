package app.knotwork.android.presentation.ui.discover

import java.util.Locale

/** Threshold at which a count abbreviates to thousands. */
private const val THOUSAND = 1_000

/** Threshold at which a count abbreviates to millions. */
private const val MILLION = 1_000_000

/**
 * Thousands/millions abbreviation for Hugging Face stats (`12400` → `"12.4k"`,
 * `2_100_000` → `"2.1M"`). Shared by the Discover list and detail screens so
 * the download/like figures format identically. Uses [Locale.US] for stable
 * numerals.
 *
 * @param value a non-negative count.
 */
internal fun formatHfCount(value: Int): String = when {
    value >= MILLION -> String.format(Locale.US, "%.1fM", value / MILLION.toDouble())
    value >= THOUSAND -> String.format(Locale.US, "%.1fk", value / THOUSAND.toDouble())
    else -> value.toString()
}
