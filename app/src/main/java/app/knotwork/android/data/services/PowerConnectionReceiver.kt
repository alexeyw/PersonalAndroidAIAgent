package app.knotwork.android.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber

/**
 * Manifest-declared receiver that makes **charging** triggers event-driven
 * instead of poll-driven.
 *
 * `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` are on the
 * implicit-broadcast exception list, so a manifest receiver is delivered them
 * even when the app process is dead — letting a charging trigger fire the
 * moment the cable is plugged in rather than waiting for the next 15-minute
 * [WorkManagerTriggerScheduler] poll.
 *
 * The receiver itself does no work: it enqueues a one-shot
 * [ChargingTriggerSweepWorker] (so the actual evaluation runs off the receiver's
 * short main-thread window, with Hilt-injected dependencies). A connect fires
 * the trigger; a disconnect lets [FireTriggerUseCase]'s armed-edge latch re-arm
 * it, so an unplug-then-replug fires again without waiting for a poll.
 * [ExistingWorkPolicy.KEEP] coalesces rapid plug/unplug bounces into a single
 * in-flight sweep, which reads the live power state when it runs.
 *
 * Network triggers stay on the poll path: `CONNECTIVITY_ACTION` is **not**
 * exempt from the implicit-broadcast ban, so it cannot be observed from a
 * manifest receiver.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isPowerEdge(intent.action)) return
        Timber.tag(TAG).d("Power edge %s — sweeping charging triggers.", intent.action)
        enqueueSweep(WorkManager.getInstance(context))
    }

    companion object {
        private const val TAG = "Trigger"

        /** Whether [action] is a power-cable connect/disconnect edge this receiver acts on. */
        fun isPowerEdge(action: String?): Boolean =
            action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED

        /**
         * Enqueues the coalescing one-shot charging sweep. Extracted (taking the
         * [WorkManager] explicitly) so the enqueue contract is unit-testable
         * without mocking the `WorkManager.getInstance` static.
         *
         * @param workManager The WorkManager to enqueue into.
         */
        fun enqueueSweep(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                ChargingTriggerSweepWorker.UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ChargingTriggerSweepWorker>().build(),
            )
        }
    }
}
