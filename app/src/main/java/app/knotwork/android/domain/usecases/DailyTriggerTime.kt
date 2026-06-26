package app.knotwork.android.domain.usecases

import java.time.Instant
import java.time.ZoneId

/**
 * Pure device-local time math for [app.knotwork.android.domain.models.TriggerCondition.DailySchedule].
 *
 * Single home for the "resolve hour:minute to an instant" arithmetic so the
 * firing decision (`EvaluateTriggerFiringUseCase`, domain) and the background
 * scheduling (`WorkManagerTriggerScheduler`, data) compute the same wall-clock
 * instant — otherwise the worker could wake at one time while the evaluator
 * judges "not yet" / "already fired" against a slightly different one. Hour and
 * minute are coerced into valid ranges so a corrupt stored value can never throw
 * a `DateTimeException`.
 */
object DailyTriggerTime {

    /** Minimum valid hour (inclusive). */
    const val MIN_HOUR: Int = 0

    /** Maximum valid hour (inclusive). */
    const val MAX_HOUR: Int = 23

    /** Minimum valid minute (inclusive). */
    const val MIN_MINUTE: Int = 0

    /** Maximum valid minute (inclusive). */
    const val MAX_MINUTE: Int = 59

    /**
     * Resolves today's `hour:minute` in [zone] to epoch-millis.
     *
     * @param hour Target hour (coerced to `0..23`).
     * @param minute Target minute (coerced to `0..59`).
     * @param nowMillis Reference time (epoch-millis) whose calendar day is used.
     * @param zone Device-local zone.
     * @return Epoch-millis of today's `hour:minute`.
     */
    fun instantTodayMillis(hour: Int, minute: Int, nowMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return today
            .atTime(hour.coerceIn(MIN_HOUR, MAX_HOUR), minute.coerceIn(MIN_MINUTE, MAX_MINUTE))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Milliseconds from [nowMillis] until the next occurrence of `hour:minute`
     * in [zone] — today's instant if it is still ahead, otherwise tomorrow's.
     *
     * @param hour Target hour (coerced to `0..23`).
     * @param minute Target minute (coerced to `0..59`).
     * @param nowMillis Reference time (epoch-millis).
     * @param zone Device-local zone.
     * @return Non-negative delay in milliseconds until the next occurrence.
     */
    fun millisUntilNext(hour: Int, minute: Int, nowMillis: Long, zone: ZoneId): Long {
        val todayInstant = instantTodayMillis(hour, minute, nowMillis, zone)
        if (todayInstant > nowMillis) return todayInstant - nowMillis
        val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
        val tomorrowInstant = tomorrow
            .atTime(hour.coerceIn(MIN_HOUR, MAX_HOUR), minute.coerceIn(MIN_MINUTE, MAX_MINUTE))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return tomorrowInstant - nowMillis
    }
}
