package app.knotwork.android.data.services

import android.annotation.SuppressLint
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the WorkManager scheduling of [RunRetentionWorker].
 *
 * A single daily periodic job constrained to **charging + device-idle**
 * (plus battery-not-low) — the same maintenance window as
 * [MemoryCompactionScheduler], because both passes are pure database tidying
 * whose only cost of delay is a slightly larger table. There is no
 * out-of-schedule trigger: run records accumulate at human pace (one row per
 * pipeline run), so no hard-limit safety net is warranted; for the same
 * reason the `pipeline_runs` table carries no extra retention index — at
 * per-session-capped row counts a daily scan inside the idle window is
 * cheaper than maintaining an index on every run write.
 *
 * The job is enqueued under a unique name with
 * [ExistingPeriodicWorkPolicy.KEEP], so re-scheduling on every cold start
 * never stacks duplicates.
 *
 * @property workManager The WorkManager instance work is enqueued on.
 */
@Singleton
class RunRetentionScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * Enqueues (or keeps) the daily periodic retention job. Idempotent: a
     * second call while the job is already scheduled is a no-op thanks to
     * [ExistingPeriodicWorkPolicy.KEEP], so it is safe to call on every cold
     * start.
     */
    @SuppressLint("IdleBatteryChargingConstraints")
    fun schedulePeriodic() {
        // Charging + device-idle is intentional: retention is housekeeping
        // that must never compete with foreground use. Lint warns the two
        // constraints may rarely co-occur on some devices; a skipped pass
        // costs nothing but a slightly larger table until the next window.
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RunRetentionWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            RunRetentionWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.tag(TAG).d("Scheduled periodic run retention (every %d h)", PERIOD_HOURS)
    }

    private companion object {
        const val TAG = "RunRetention"

        /** Periodic interval — once per day. */
        const val PERIOD_HOURS = 24L

        /**
         * Flex window — WorkManager may run the job any time in the last
         * [FLEX_HOURS] of each period once constraints are met, giving the OS
         * latitude to batch it with the other maintenance jobs.
         */
        const val FLEX_HOURS = 6L
    }
}
