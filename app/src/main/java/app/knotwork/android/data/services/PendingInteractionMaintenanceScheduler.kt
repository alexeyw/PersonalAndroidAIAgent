package app.knotwork.android.data.services

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic [PendingInteractionMaintenanceWorker] expiry pass.
 *
 * Runs every [PERIOD_HOURS] hours with only a battery-not-low constraint —
 * intentionally looser than the memory-compaction maintenance window
 * (charging + idle): the pass is a few Room reads and notification cancels,
 * and an unanswered park should fail close to its window boundary, not
 * whenever the device next charges overnight. The granularity bound this
 * gives expiry (a park can outlive its window by up to one period) is
 * acceptable because the window itself is measured in hours–days, and the
 * decision-time lazy check still rejects expired responses exactly.
 */
@Singleton
class PendingInteractionMaintenanceScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * Enqueues (or keeps) the periodic expiry job. Idempotent: a second call
     * while the job is already scheduled is a no-op thanks to
     * [ExistingPeriodicWorkPolicy.KEEP], so it is safe to call on every cold
     * start.
     */
    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PendingInteractionMaintenanceWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PendingInteractionMaintenanceWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.tag(TAG).d("Scheduled periodic pending-interaction expiry (every %d h)", PERIOD_HOURS)
    }

    private companion object {
        const val TAG = "PendingMaintenance"

        /** Repeat interval of the expiry pass, in hours. */
        const val PERIOD_HOURS = 6L
    }
}
