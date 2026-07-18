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
import org.junit.Assert.assertNotEquals
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
    fun `given same prompt and interval but different sessions when schedulePeriodic then unique names differ`() {
        val names = mutableListOf<String>()

        scheduler.schedulePeriodic(
            "check emails",
            intervalHours = 2,
            sessionId = "session-a",
            constraints = constraints,
        )
        scheduler.schedulePeriodic(
            "check emails",
            intervalHours = 2,
            sessionId = "session-b",
            constraints = constraints,
        )

        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(capture(names), any(), any<PeriodicWorkRequest>())
        }
        // Schedules bound to different sessions must not collapse onto each other:
        // each session keeps its own recurring task so results land where intended.
        assertNotEquals(names[0], names[1])
    }

    @Test
    fun `given prompts differing only by surrounding whitespace when schedulePeriodic then unique names match`() {
        val names = mutableListOf<String>()

        scheduler.schedulePeriodic(
            "check emails ",
            intervalHours = 2,
            sessionId = "session-a",
            constraints = constraints,
        )
        scheduler.schedulePeriodic(
            "check emails",
            intervalHours = 2,
            sessionId = "session-a",
            constraints = constraints,
        )

        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(capture(names), any(), any<PeriodicWorkRequest>())
        }
        // A stray trailing space must not slip past the de-dup and stack a duplicate.
        assertEquals(names[0], names[1])
    }

    @Test
    fun `given prompt with surrounding whitespace when schedulePeriodic then input data keeps the verbatim prompt`() {
        val requestSlot = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic(
            "check emails ",
            intervalHours = 2,
            sessionId = "session-a",
            constraints = constraints,
        )

        verify { workManager.enqueueUniquePeriodicWork(any(), any(), capture(requestSlot)) }
        // Trimming is a de-dup-key concern only; the worker still runs the exact prompt.
        assertEquals("check emails ", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_PROMPT))
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
    fun `given a pre-minted run id when scheduleOneTime then carries it into input data`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime(
            "evening journal",
            delayMinutes = 0,
            sessionId = "session-7",
            constraints = constraints,
            runId = "trigger-run-1",
        )

        verify { workManager.enqueue(capture(requestSlot)) }
        // The id survives into input data so AgentWorker can reuse it verbatim as
        // the run id the trigger's journal row already references.
        assertEquals("trigger-run-1", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_RUN_ID))
    }

    @Test
    fun `given no run id when scheduleOneTime then leaves the run-id key absent`() {
        val requestSlot = slot<OneTimeWorkRequest>()

        scheduler.scheduleOneTime("check emails once", delayMinutes = 0, sessionId = null, constraints = constraints)

        verify { workManager.enqueue(capture(requestSlot)) }
        assertNull(requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_RUN_ID))
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
