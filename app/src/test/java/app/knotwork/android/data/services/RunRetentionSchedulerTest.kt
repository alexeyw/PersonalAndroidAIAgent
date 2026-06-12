package app.knotwork.android.data.services

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RunRetentionScheduler]: the daily retention job is
 * enqueued under its unique name with the KEEP policy (idempotent cold-start
 * scheduling) and carries the charging + idle + battery-not-low maintenance
 * constraints.
 */
class RunRetentionSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: RunRetentionScheduler

    @Before
    fun setup() {
        workManager = mockk(relaxed = true)
        scheduler = RunRetentionScheduler(workManager)
    }

    @Test
    fun `schedulePeriodic enqueues a unique periodic work kept on re-schedule`() {
        val request = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                RunRetentionWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                capture(request),
            )
        }
        assertTrue(request.isCaptured)
    }

    @Test
    fun `schedulePeriodic constrains the job to the maintenance window`() {
        val request = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic()

        verify { workManager.enqueueUniquePeriodicWork(any(), any(), capture(request)) }
        val constraints = request.captured.workSpec.constraints
        assertTrue(constraints.requiresCharging())
        assertTrue(constraints.requiresDeviceIdle())
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `schedulePeriodic is idempotent across repeated cold starts`() {
        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()

        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(
                RunRetentionWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>(),
            )
        }
    }
}
