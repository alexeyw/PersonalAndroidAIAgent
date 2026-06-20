package app.knotwork.android.data.services

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import app.knotwork.android.domain.services.ScheduledTaskConstraints
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkManagerTaskSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerTaskScheduler

    private val constraints = ScheduledTaskConstraints(requiresBatteryNotLow = true)

    @Before
    fun setup() {
        workManager = mockk()
        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()
        every {
            workManager.enqueueUniquePeriodicWork(
                any(),
                any(),
                any<PeriodicWorkRequest>(),
            )
        } returns mockk()
        scheduler = WorkManagerTaskScheduler(workManager)
    }

    @Test
    fun `given positive interval when schedulePeriodic then enqueues unique periodic work keyed by interval`() {
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic("check emails", intervalHours = 2, sessionId = null, constraints = constraints)

        verify {
            workManager.enqueueUniquePeriodicWork(
                capture(nameSlot),
                capture(policySlot),
                capture(requestSlot),
            )
        }
        assertTrue(nameSlot.captured.contains("check emails"))
        assertTrue(nameSlot.captured.contains("2h"))
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
        // The periodic request is built at the requested cadence (hours), carries
        // the prompt, and maps the battery constraint — parity with the one-time path.
        assertEquals(2L * 60L * 60L * 1000L, requestSlot.captured.workSpec.intervalDuration)
        assertEquals("check emails", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_PROMPT))
        assertTrue(requestSlot.captured.workSpec.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `given delay when scheduleOneTime then enqueues one-time work with the initial delay`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime("check emails once", delayMinutes = 10, sessionId = null, constraints = constraints)

        verify { workManager.enqueue(capture(requestSlot)) }
        assertEquals(
            10L * 60L * 1000L,
            requestSlot.captured.workSpec.initialDelay,
        )
    }

    @Test
    fun `given no delay when scheduleOneTime then enqueues one-time work without an initial delay`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime("run now", delayMinutes = 0, sessionId = null, constraints = constraints)

        verify { workManager.enqueue(capture(requestSlot)) }
        assertEquals(0L, requestSlot.captured.workSpec.initialDelay)
    }

    @Test
    fun `given session id when scheduleOneTime then carries prompt and session id into input data`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime(
            "check emails once",
            delayMinutes = 0,
            sessionId = "session-7",
            constraints = constraints,
        )

        verify { workManager.enqueue(capture(requestSlot)) }
        assertEquals("session-7", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
        assertEquals("check emails once", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_PROMPT))
    }

    @Test
    fun `given session id when schedulePeriodic then carries it into input data`() {
        val requestSlot = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic(
            "check emails",
            intervalHours = 2,
            sessionId = "session-7",
            constraints = constraints,
        )

        verify { workManager.enqueueUniquePeriodicWork(any(), any(), capture(requestSlot)) }
        assertEquals("session-7", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
    }

    @Test
    fun `given no session id when scheduleOneTime then leaves the session key absent`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime("check emails once", delayMinutes = 0, sessionId = null, constraints = constraints)

        verify { workManager.enqueue(capture(requestSlot)) }
        assertNull(requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
    }

    @Test
    fun `given battery-not-low constraint when scheduleOneTime then maps it onto the work constraints`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime("check emails once", delayMinutes = 0, sessionId = null, constraints = constraints)

        verify { workManager.enqueue(capture(requestSlot)) }
        assertTrue(requestSlot.captured.workSpec.constraints.requiresBatteryNotLow())
        // Other constraints stay at their permissive defaults.
        assertEquals(NetworkType.NOT_REQUIRED, requestSlot.captured.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `given battery constraint disabled when scheduleOneTime then maps false onto the work constraints`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime(
            "check emails once",
            delayMinutes = 0,
            sessionId = null,
            constraints = ScheduledTaskConstraints(requiresBatteryNotLow = false),
        )

        verify { workManager.enqueue(capture(requestSlot)) }
        assertTrue(!requestSlot.captured.workSpec.constraints.requiresBatteryNotLow())
    }
}
