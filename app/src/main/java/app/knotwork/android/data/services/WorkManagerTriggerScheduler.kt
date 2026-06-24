package app.knotwork.android.data.services

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.services.TriggerScheduler
import app.knotwork.android.domain.usecases.DailyTriggerTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TriggerScheduler] implementation backed by Jetpack `WorkManager`.
 *
 * Each active trigger becomes a unique **periodic** work request that wakes
 * [TriggerWatchWorker]:
 * - **interval schedule** → the trigger's own interval (clamped to the platform
 *   15-minute floor) with an explicit flex of half the interval, so consecutive
 *   wakes are spaced ~one interval apart and the evaluator's half-interval
 *   debounce never drops a cycle;
 * - **daily schedule** → a 24-hour period with an initial delay to the next
 *   `hh:mm` (computed by the shared [DailyTriggerTime]);
 * - **event** (charging / network) → an **unconstrained** 15-minute poll. The
 *   wakeup is deliberately *not* gated on a charging/network constraint: the
 *   worker must observe both the satisfied and unsatisfied states to detect the
 *   edge (and re-arm), and the evaluator — not a WorkManager constraint — gates
 *   the actual fire. This also keeps the "Wi-Fi only" predicate single-sourced
 *   in the evaluator instead of split across a constraint that means "unmetered".
 *
 * Periodic work is persisted by WorkManager across reboot, so no boot receiver
 * is required — an idempotent [syncAll] on cold start re-registers the active
 * set. A watch whose trigger was deleted/disabled while the process was dead is
 * reclaimed by [TriggerWatchWorker], which cancels its own work on the first
 * wake that finds the trigger gone or inactive; [cancel] removes it immediately
 * on the live mutation path.
 */
@Singleton
class WorkManagerTriggerScheduler @Inject constructor(private val workManager: WorkManager) : TriggerScheduler {

    override fun syncAll(activeTriggers: List<Trigger>) {
        // Register (or refresh) every active trigger. Triggers that dropped out
        // of the active set are reclaimed by the self-cancelling worker (across
        // process death) or by an explicit cancel() on the live mutation path —
        // there is no in-memory mirror to drift.
        activeTriggers.forEach { register(it) }
    }

    override fun register(trigger: Trigger) {
        if (!trigger.isActive) {
            cancel(trigger.id)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            uniqueName(trigger.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(trigger),
        )
    }

    override fun cancel(triggerId: String) {
        workManager.cancelUniqueWork(uniqueName(triggerId))
    }

    /** Builds the periodic watch request for [trigger]'s condition. */
    private fun buildRequest(trigger: Trigger): PeriodicWorkRequest {
        val inputData = Data.Builder().putString(TriggerWatchWorker.KEY_TRIGGER_ID, trigger.id).build()
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

        val builder = when (val condition = trigger.condition) {
            is TriggerCondition.IntervalSchedule -> {
                val periodMinutes = condition.intervalMinutes.coerceAtLeast(MIN_PERIOD_MINUTES)
                val flexMinutes = (periodMinutes / FLEX_DIVISOR).coerceIn(MIN_FLEX_MINUTES, periodMinutes)
                PeriodicWorkRequestBuilder<TriggerWatchWorker>(
                    periodMinutes,
                    TimeUnit.MINUTES,
                    flexMinutes,
                    TimeUnit.MINUTES,
                )
            }

            is TriggerCondition.DailySchedule ->
                PeriodicWorkRequestBuilder<TriggerWatchWorker>(DAY_MINUTES, TimeUnit.MINUTES)
                    .setInitialDelay(initialDelayToDailyMillis(condition), TimeUnit.MILLISECONDS)

            TriggerCondition.Charging, is TriggerCondition.NetworkConnected ->
                PeriodicWorkRequestBuilder<TriggerWatchWorker>(POLL_PERIOD_MINUTES, TimeUnit.MINUTES)
        }

        return builder.setInputData(inputData).setConstraints(constraints).build()
    }

    /** Milliseconds from now until the next device-local occurrence of the daily time. */
    private fun initialDelayToDailyMillis(
        condition: TriggerCondition.DailySchedule,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = DailyTriggerTime.millisUntilNext(condition.hour, condition.minute, nowMillis, zone)

    /** Per-trigger unique work name; the id suffix keeps each watch independent. */
    private fun uniqueName(triggerId: String): String = TriggerWatchWorker.UNIQUE_NAME_PREFIX + triggerId

    private companion object {
        /** Platform periodic-work floor (minutes). */
        const val MIN_PERIOD_MINUTES = 15L

        /** Poll period for event triggers (charging / network) — the platform floor. */
        const val POLL_PERIOD_MINUTES = 15L

        /** One day in minutes — the period of a daily schedule. */
        const val DAY_MINUTES = 24L * 60L

        /** Interval is divided by this to derive the flex window (half the interval). */
        const val FLEX_DIVISOR = 2L

        /** Platform minimum flex window (minutes). */
        const val MIN_FLEX_MINUTES = 5L
    }
}
