package app.knotwork.android.data.services

import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric coverage for [PowerConnectionReceiver] — the manifest receiver
 * that turns a power-cable edge into an immediate charging-trigger sweep.
 *
 * The receiver's two seams are tested directly: [PowerConnectionReceiver.isPowerEdge]
 * (which actions it acts on) and [PowerConnectionReceiver.enqueueSweep] (the
 * enqueue contract — unique name + coalescing KEEP policy). The sweep worker's
 * own behaviour is covered by [ChargingTriggerSweepWorkerTest].
 */
@RunWith(RobolectricTestRunner::class)
class PowerConnectionReceiverTest {

    @Test
    fun `given a power connect or disconnect edge then isPowerEdge is true`() {
        assertTrue(PowerConnectionReceiver.isPowerEdge(Intent.ACTION_POWER_CONNECTED))
        assertTrue(PowerConnectionReceiver.isPowerEdge(Intent.ACTION_POWER_DISCONNECTED))
    }

    @Test
    fun `given an unrelated or null action then isPowerEdge is false`() {
        assertFalse(PowerConnectionReceiver.isPowerEdge(Intent.ACTION_BATTERY_LOW))
        assertFalse(PowerConnectionReceiver.isPowerEdge(null))
    }

    @Test
    fun `given enqueueSweep then enqueues the unique charging sweep with the coalescing KEEP policy`() {
        val workManager = mockk<WorkManager>(relaxed = true)

        PowerConnectionReceiver.enqueueSweep(workManager)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                ChargingTriggerSweepWorker.UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
