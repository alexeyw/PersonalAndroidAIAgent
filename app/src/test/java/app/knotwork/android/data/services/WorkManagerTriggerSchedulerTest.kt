package app.knotwork.android.data.services

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WorkManagerTriggerScheduler] — the mapping from a [Trigger]'s
 * condition onto a constraint-gated periodic WorkManager request, plus the
 * register/cancel/sync reconciliation. WorkManager is mocked and the captured
 * request's `workSpec` is inspected, mirroring [WorkManagerTaskSchedulerTest].
 */
class WorkManagerTriggerSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerTriggerScheduler

    private val fifteenMinutesMs = 15L * 60L * 1000L

    @Before
    fun setup() {
        workManager = mockk()
        every {
            workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())
        } returns mockk()
        every { workManager.cancelUniqueWork(any()) } returns mockk()
        scheduler = WorkManagerTriggerScheduler(workManager)
    }

    private fun trigger(
        id: String = "t1",
        condition: TriggerCondition,
        enabled: Boolean = true,
        pipelineId: String? = "pipe-1",
    ): Trigger = Trigger(
        id = id,
        name = "T",
        condition = condition,
        pipelineId = pipelineId,
        prompt = "go",
        enabled = enabled,
        createdAt = 0L,
    )

    private fun captureRegister(condition: TriggerCondition, id: String = "t1"): PeriodicWorkRequest {
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        scheduler.register(trigger(id = id, condition = condition))

        verify {
            workManager.enqueueUniquePeriodicWork(capture(nameSlot), capture(policySlot), capture(requestSlot))
        }
        assertEquals(TriggerWatchWorker.UNIQUE_NAME_PREFIX + id, nameSlot.captured)
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policySlot.captured)
        assertEquals(id, requestSlot.captured.workSpec.input.getString(TriggerWatchWorker.KEY_TRIGGER_ID))
        return requestSlot.captured
    }

    @Test
    fun `given charging trigger when registered then requires charging at the poll floor`() {
        val request = captureRegister(TriggerCondition.Charging)

        assertTrue(request.workSpec.constraints.requiresCharging())
        assertEquals(fifteenMinutesMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `given interval trigger when registered then runs at the requested cadence`() {
        val request = captureRegister(TriggerCondition.IntervalSchedule(intervalMinutes = 30))

        assertEquals(30L * 60L * 1000L, request.workSpec.intervalDuration)
    }

    @Test
    fun `given interval below the floor when registered then clamped to fifteen minutes`() {
        val request = captureRegister(TriggerCondition.IntervalSchedule(intervalMinutes = 5))

        assertEquals(fifteenMinutesMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `given wifi-only network trigger when registered then requires an unmetered network`() {
        val request = captureRegister(TriggerCondition.NetworkConnected(wifiOnly = true))

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `given any-network trigger when registered then requires a connected network`() {
        val request = captureRegister(TriggerCondition.NetworkConnected(wifiOnly = false))

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `given daily trigger when registered then runs daily with an initial delay`() {
        val request = captureRegister(TriggerCondition.DailySchedule(hour = 8, minute = 0))

        assertEquals(24L * 60L * 60L * 1000L, request.workSpec.intervalDuration)
        assertTrue("Daily watch must defer to the next hh:mm", request.workSpec.initialDelay > 0L)
    }

    @Test
    fun `given disabled trigger when registered then cancels instead of enqueuing`() {
        scheduler.register(trigger(condition = TriggerCondition.Charging, enabled = false))

        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "t1") }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) }
    }

    @Test
    fun `given unbound trigger when registered then cancels instead of enqueuing`() {
        scheduler.register(trigger(condition = TriggerCondition.Charging, pipelineId = null))

        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "t1") }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) }
    }

    @Test
    fun `given cancel when called then cancels the per-trigger unique work`() {
        scheduler.cancel("t9")

        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "t9") }
    }

    @Test
    fun `given a previously registered trigger when syncAll omits it then it is cancelled`() {
        scheduler.register(trigger(id = "stale", condition = TriggerCondition.Charging))

        scheduler.syncAll(listOf(trigger(id = "fresh", condition = TriggerCondition.IntervalSchedule(30))))

        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "stale") }
        verify {
            workManager.enqueueUniquePeriodicWork(
                eq(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "fresh"),
                any(),
                any<PeriodicWorkRequest>(),
            )
        }
    }
}
