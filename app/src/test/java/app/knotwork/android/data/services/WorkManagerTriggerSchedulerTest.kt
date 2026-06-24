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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WorkManagerTriggerScheduler] — the mapping from a [Trigger]'s
 * condition onto a periodic WorkManager request, plus register/cancel/sync.
 * WorkManager is mocked and the captured request's `workSpec` is inspected,
 * mirroring [WorkManagerTaskSchedulerTest].
 */
class WorkManagerTriggerSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerTriggerScheduler

    private val minuteMs = 60_000L

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
    fun `given charging trigger when registered then polls unconstrained at the floor`() {
        val request = captureRegister(TriggerCondition.Charging)

        // Edge detection needs to observe BOTH charging and not-charging, so the
        // wakeup carries no charging/network constraint — only battery-not-low.
        assertFalse(request.workSpec.constraints.requiresCharging())
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(15 * minuteMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `given wifi-only network trigger when registered then carries no network constraint`() {
        val request = captureRegister(TriggerCondition.NetworkConnected(wifiOnly = true))

        // The 'wifi only' predicate lives solely in the evaluator (isWifiConnected),
        // not in a WorkManager NetworkType that would mean 'unmetered'.
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(15 * minuteMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `given interval trigger when registered then runs at the requested cadence with half-interval flex`() {
        val request = captureRegister(TriggerCondition.IntervalSchedule(intervalMinutes = 30))

        assertEquals(30 * minuteMs, request.workSpec.intervalDuration)
        // Flex = half the interval so consecutive wakes are spaced ~one interval
        // apart (the evaluator's half-interval debounce then never drops a cycle).
        assertEquals(15 * minuteMs, request.workSpec.flexDuration)
    }

    @Test
    fun `given interval below the floor when registered then clamped to fifteen minutes`() {
        val request = captureRegister(TriggerCondition.IntervalSchedule(intervalMinutes = 5))

        assertEquals(15 * minuteMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `given daily trigger when registered then runs daily with an initial delay`() {
        val request = captureRegister(TriggerCondition.DailySchedule(hour = 8, minute = 0))

        assertEquals(24 * 60 * minuteMs, request.workSpec.intervalDuration)
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
    fun `given syncAll when called then registers every active trigger`() {
        scheduler.syncAll(
            listOf(
                trigger(id = "a", condition = TriggerCondition.Charging),
                trigger(id = "b", condition = TriggerCondition.IntervalSchedule(30)),
            ),
        )

        verify {
            workManager.enqueueUniquePeriodicWork(
                eq(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "a"),
                any(),
                any<PeriodicWorkRequest>(),
            )
        }
        verify {
            workManager.enqueueUniquePeriodicWork(
                eq(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "b"),
                any(),
                any<PeriodicWorkRequest>(),
            )
        }
    }
}
