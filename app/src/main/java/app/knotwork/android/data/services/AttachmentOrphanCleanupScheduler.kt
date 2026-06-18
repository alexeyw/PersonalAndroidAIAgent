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
 * Owns the WorkManager scheduling of [AttachmentOrphanCleanupWorker].
 *
 * A single daily periodic job constrained to **charging + device-idle** (plus
 * battery-not-low) — the same maintenance window as [RunRetentionScheduler],
 * because both passes are pure housekeeping whose only cost of delay is a
 * little extra storage held until the next window. There is no out-of-schedule
 * trigger: attachments accumulate at human pace and eager delete-with-message
 * already reclaims the common case, so the sweep is only a backstop.
 *
 * The job is enqueued under a unique name with [ExistingPeriodicWorkPolicy.KEEP],
 * so re-scheduling on every cold start never stacks duplicates.
 *
 * @property workManager The WorkManager instance work is enqueued on.
 */
@Singleton
class AttachmentOrphanCleanupScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * Enqueues (or keeps) the daily periodic orphan-cleanup job. Idempotent
     * thanks to [ExistingPeriodicWorkPolicy.KEEP], so it is safe to call on
     * every cold start.
     */
    @SuppressLint("IdleBatteryChargingConstraints")
    fun schedulePeriodic() {
        // Charging + device-idle is intentional: cleanup is housekeeping that
        // must never compete with foreground use. Lint warns the two
        // constraints may rarely co-occur on some devices; a skipped pass costs
        // nothing but a little extra storage until the next window.
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AttachmentOrphanCleanupWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AttachmentOrphanCleanupWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.tag(TAG).d("Scheduled periodic attachment orphan cleanup (every %d h)", PERIOD_HOURS)
    }

    private companion object {
        const val TAG = "AttachmentCleanup"

        /** Periodic interval — once per day. */
        const val PERIOD_HOURS = 24L

        /**
         * Flex window — WorkManager may run the job any time in the last
         * [FLEX_HOURS] of each period once constraints are met, batching it with
         * the other maintenance jobs.
         */
        const val FLEX_HOURS = 6L
    }
}
