package app.knotwork.android.presentation.ui.discover

import java.util.Locale

/** Threshold at which a count abbreviates to thousands. */
private const val THOUSAND = 1_000

/** Threshold at which a count abbreviates to millions. */
private const val MILLION = 1_000_000

/**
 * Smallest value that, divided by 1000 and rounded to one decimal, reaches
 * 1000.0 — i.e. the point at which the "k" suffix would print "1000.0k" and we
 * must promote to "M" instead. Keeps the unit boundary aligned with the
 * half-up rounding of `"%.1f"`.
 */
private const val MILLION_ROUNDING_FLOOR = 999_950

/**
 * Thousands/millions abbreviation for Hugging Face stats (`12400` → `"12.4k"`,
 * `2_100_000` → `"2.1M"`). Shared by the Discover list and detail screens so
 * the download/like figures format identically. Uses [Locale.US] for stable
 * numerals. The "M" branch threshold is the rounding floor (999,950), so a
 * value like 999,999 prints "1.0M" rather than the nonsensical "1000.0k".
 *
 * @param value a non-negative count.
 */
internal fun formatHfCount(value: Int): String = when {
    value >= MILLION_ROUNDING_FLOOR -> String.format(Locale.US, "%.1fM", value / MILLION.toDouble())
    value >= THOUSAND -> String.format(Locale.US, "%.1fk", value / THOUSAND.toDouble())
    else -> value.toString()
}
