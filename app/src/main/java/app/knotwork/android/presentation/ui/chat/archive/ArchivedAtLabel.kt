package app.knotwork.android.presentation.ui.chat.archive

import java.util.concurrent.TimeUnit

/**
 * Coarse bucket describing how long ago a chat was archived.
 *
 * The archive labels **when the user put the chat away**, relative, because at
 * archive time-scales (hours → weeks) "2 h ago" answers the question the user
 * is actually asking; the drawer's absolute `EEE HH:mm` is for a list they scan
 * several times a day.
 *
 * Modelled as a bucket rather than a formatted string so the ladder is pure,
 * testable Kotlin and the localisation (including plural forms) stays in the
 * resource layer where it belongs.
 */
sealed interface ArchivedAtLabel {

    /** Archived less than a minute ago. */
    data object JustNow : ArchivedAtLabel

    /** Archived [minutes] minutes ago (1..59). */
    data class Minutes(val minutes: Int) : ArchivedAtLabel

    /** Archived [hours] hours ago (1..23). */
    data class Hours(val hours: Int) : ArchivedAtLabel

    /** Archived roughly a day ago. */
    data object Yesterday : ArchivedAtLabel

    /** Archived [days] days ago (2..6). */
    data class Days(val days: Int) : ArchivedAtLabel

    /** Archived [weeks] weeks ago (1..4). */
    data class Weeks(val weeks: Int) : ArchivedAtLabel

    /** Archived [months] months ago (1..11, a month approximated as 30 days). */
    data class Months(val months: Int) : ArchivedAtLabel

    /** Archived [years] years or more ago. */
    data class Years(val years: Int) : ArchivedAtLabel

    /**
     * The archive instant is unknown — the row was archived before the app
     * recorded one. Callers render a neutral label rather than inventing a time.
     */
    data object Unknown : ArchivedAtLabel
}

/** Days in the approximate month used by the label ladder. */
private const val DAYS_PER_MONTH = 30

/** Days in the approximate year used by the label ladder. */
private const val DAYS_PER_YEAR = 365

/** Days in a week. */
private const val DAYS_PER_WEEK = 7

/** Upper bound (exclusive) of the "N d ago" bucket, in days. */
private const val DAYS_BUCKET_LIMIT = 7

/**
 * Buckets [archivedAt] relative to [now].
 *
 * A `null` [archivedAt] yields [ArchivedAtLabel.Unknown]; so does a future
 * instant, which can only come from a clock change and must not render as a
 * negative duration.
 *
 * @param archivedAt epoch-millis at which the chat was archived, or `null`.
 * @param now reference instant (epoch-millis), injectable for tests.
 * @return the bucket describing the elapsed time.
 */
@Suppress("ReturnCount") // A descending ladder of buckets; early returns are the clearest form.
fun archivedAtLabel(archivedAt: Long?, now: Long): ArchivedAtLabel {
    if (archivedAt == null) return ArchivedAtLabel.Unknown
    val elapsedMs = now - archivedAt
    if (elapsedMs < 0) return ArchivedAtLabel.Unknown

    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
    if (minutes < 1) return ArchivedAtLabel.JustNow
    if (minutes < TimeUnit.HOURS.toMinutes(1)) return ArchivedAtLabel.Minutes(minutes.toInt())

    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
    if (hours < TimeUnit.DAYS.toHours(1)) return ArchivedAtLabel.Hours(hours.toInt())

    val days = TimeUnit.MILLISECONDS.toDays(elapsedMs).toInt()
    if (days < 2) return ArchivedAtLabel.Yesterday
    if (days < DAYS_BUCKET_LIMIT) return ArchivedAtLabel.Days(days)
    if (days < DAYS_PER_MONTH) return ArchivedAtLabel.Weeks(days / DAYS_PER_WEEK)
    if (days < DAYS_PER_YEAR) return ArchivedAtLabel.Months(days / DAYS_PER_MONTH)
    return ArchivedAtLabel.Years(days / DAYS_PER_YEAR)
}
