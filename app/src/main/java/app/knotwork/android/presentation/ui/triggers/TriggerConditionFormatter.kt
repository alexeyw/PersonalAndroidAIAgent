package app.knotwork.android.presentation.ui.triggers

import app.knotwork.android.domain.models.TriggerCondition

/**
 * Structured, locale-neutral rendering of a [TriggerCondition]. The pure
 * [TriggerConditionFormatter] decides *which* template and *what* numeric values
 * a condition maps to; the Compose layer turns this into a localized string via
 * `stringResource` / `pluralStringResource`. Splitting the two keeps the
 * branching logic (hours-vs-minutes, Wi-Fi-vs-any) unit-testable without an
 * Android `Context`.
 */
sealed interface TriggerConditionLabel {

    /** "Every N minutes" — sub-hour or non-round intervals. */
    data class IntervalMinutes(val minutes: Int) : TriggerConditionLabel

    /** "Every N hours" — whole-hour intervals. */
    data class IntervalHours(val hours: Int) : TriggerConditionLabel

    /** "Every day at HH:mm". */
    data class Daily(val hour: Int, val minute: Int) : TriggerConditionLabel

    /** "When charging connected". */
    data object Charging : TriggerConditionLabel

    /** "When connected to any network". */
    data object NetworkAny : TriggerConditionLabel

    /** "When Wi-Fi connects". */
    data object NetworkWifi : TriggerConditionLabel

    /**
     * "When joining <names>" — Wi-Fi scoped to one or more named networks.
     *
     * @property ssids the network names the trigger is scoped to (non-empty).
     */
    data class NetworkNamed(val ssids: List<String>) : TriggerConditionLabel
}

/**
 * Maps a domain [TriggerCondition] to a [TriggerConditionLabel].
 *
 * Pure and side-effect free so every branch — the hours/minutes threshold, the
 * Wi-Fi/any split, the daily time — can be asserted in unit tests; the localized
 * strings are applied separately in the presentation layer.
 */
object TriggerConditionFormatter {

    /** Minimum interval the schedule chips and validation enforce (WorkManager floor). */
    const val MIN_INTERVAL_MINUTES: Long = 15L

    /** Minutes per hour — the threshold/divisor for the hours-vs-minutes split. */
    private const val MINUTES_PER_HOUR = 60L

    /**
     * Projects [condition] onto its display label.
     *
     * Interval conditions render as whole hours only when the stored minutes are
     * a non-zero exact multiple of 60 (e.g. 360 → "6 hours"); otherwise they
     * render in minutes (e.g. 90 → "90 minutes"). A non-positive stored value is
     * floored to one minute for display so it never reads as "0 minutes".
     *
     * @param condition the domain condition to format.
     * @return the structured label the UI resolves to a localized string.
     */
    fun toLabel(condition: TriggerCondition): TriggerConditionLabel = when (condition) {
        is TriggerCondition.IntervalSchedule -> {
            val minutes = condition.intervalMinutes.coerceAtLeast(1L)
            if (minutes % MINUTES_PER_HOUR == 0L) {
                TriggerConditionLabel.IntervalHours((minutes / MINUTES_PER_HOUR).toInt())
            } else {
                TriggerConditionLabel.IntervalMinutes(minutes.toInt())
            }
        }

        is TriggerCondition.DailySchedule -> TriggerConditionLabel.Daily(condition.hour, condition.minute)

        TriggerCondition.Charging -> TriggerConditionLabel.Charging

        is TriggerCondition.NetworkConnected -> when {
            condition.ssids.isNotEmpty() -> TriggerConditionLabel.NetworkNamed(condition.ssids)
            condition.wifiOnly -> TriggerConditionLabel.NetworkWifi
            else -> TriggerConditionLabel.NetworkAny
        }
    }
}
