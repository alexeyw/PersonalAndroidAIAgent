package app.knotwork.android.data.services

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.services.TriggerScheduler
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TriggerScheduler] implementation backed by Jetpack `WorkManager`.
 *
 * Each active trigger becomes a unique **periodic** work request that wakes
 * [TriggerWatchWorker] when the trigger's coarse constraint holds:
 * - charging → `setRequiresCharging(true)`;
 * - network / Wi-Fi → `setRequiredNetworkType(CONNECTED | UNMETERED)`;
 * - interval schedule → the trigger's own interval (clamped to the platform
 *   15-minute periodic floor);
 * - daily schedule → a 24-hour period with an initial delay to the next
 *   `hh:mm`.
 *
 * The constraints are *level-triggered*, so the worker re-checks the live
 * condition and applies a re-arm debounce
 * ([app.knotwork.android.domain.usecases.EvaluateTriggerFiringUseCase]); a
 * still-satisfied constraint therefore cannot enqueue duplicate runs. Periodic
 * work is persisted by WorkManager across reboot, so no boot receiver is
 * required — an idempotent [syncAll] on cold start is enough.
 *
 * A stale watch (its trigger disabled/deleted while the process was dead) is
 * self-neutralising: the worker re-loads the trigger and the evaluator returns a
 * skip. Explicit [cancel] from the mutation path removes the wakeup entirely.
 */
@Singleton
class WorkManagerTriggerScheduler @Inject constructor(private val workManager: WorkManager) : TriggerScheduler {

    /**
     * Trigger ids registered in this process, so [syncAll] can cancel a watch
     * that has dropped out of the active set since the last sync. Reset on
     * process death (WorkManager keeps the actual work), which the
     * self-neutralising-wake property tolerates.
     */
    private val registered: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun syncAll(activeTriggers: List<Trigger>) {
        val activeIds = activeTriggers.map { it.id }.toSet()
        val stale = synchronized(registered) { registered.toSet() } - activeIds
        stale.forEach { cancel(it) }
        activeTriggers.forEach { register(it) }
    }

    override fun register(trigger: Trigger) {
        if (!trigger.isActive) {
            cancel(trigger.id)
            return
        }
        val request = buildRequest(trigger)
        workManager.enqueueUniquePeriodicWork(uniqueName(trigger.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        registered.add(trigger.id)
    }

    override fun cancel(triggerId: String) {
        workManager.cancelUniqueWork(uniqueName(triggerId))
        registered.remove(triggerId)
    }

    /** Builds the periodic watch request for [trigger]'s condition. */
    private fun buildRequest(trigger: Trigger): PeriodicWorkRequest {
        val intervalMinutes = periodMinutesFor(trigger.condition)
        val builder = PeriodicWorkRequestBuilder<TriggerWatchWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(TriggerWatchWorker.KEY_TRIGGER_ID, trigger.id).build())
            .setConstraints(constraintsFor(trigger.condition))

        (trigger.condition as? TriggerCondition.DailySchedule)?.let { daily ->
            builder.setInitialDelay(initialDelayToDailyMillis(daily.hour, daily.minute), TimeUnit.MILLISECONDS)
        }
        return builder.build()
    }

    /** The periodic interval (minutes) for a condition, never below the platform floor. */
    private fun periodMinutesFor(condition: TriggerCondition): Long = when (condition) {
        is TriggerCondition.IntervalSchedule -> condition.intervalMinutes.coerceAtLeast(MIN_PERIOD_MINUTES)
        is TriggerCondition.DailySchedule -> DAY_MINUTES
        TriggerCondition.Charging, is TriggerCondition.NetworkConnected -> POLL_PERIOD_MINUTES
    }

    /** WorkManager constraints gating a condition's wakeups. */
    private fun constraintsFor(condition: TriggerCondition): Constraints {
        val builder = Constraints.Builder().setRequiresBatteryNotLow(true)
        when (condition) {
            TriggerCondition.Charging -> builder.setRequiresCharging(true)
            is TriggerCondition.NetworkConnected ->
                builder.setRequiredNetworkType(if (condition.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            is TriggerCondition.IntervalSchedule, is TriggerCondition.DailySchedule -> Unit
        }
        return builder.build()
    }

    /** Milliseconds from now until the next device-local `hour:minute`. */
    private fun initialDelayToDailyMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var next = now.toLocalDate()
            .atTime(hour.coerceIn(MIN_HOUR, MAX_HOUR), minute.coerceIn(MIN_MINUTE, MAX_MINUTE))
            .atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    /** Per-trigger unique work name; the id suffix keeps each watch independent. */
    private fun uniqueName(triggerId: String): String = TriggerWatchWorker.UNIQUE_NAME_PREFIX + triggerId

    private companion object {
        /** Platform periodic-work floor (minutes). */
        const val MIN_PERIOD_MINUTES = 15L

        /** Poll period for event triggers (charging / network) — the platform floor. */
        const val POLL_PERIOD_MINUTES = 15L

        /** One day in minutes — the period of a daily schedule. */
        const val DAY_MINUTES = 24L * 60L

        const val MIN_HOUR = 0
        const val MAX_HOUR = 23
        const val MIN_MINUTE = 0
        const val MAX_MINUTE = 59
    }
}
