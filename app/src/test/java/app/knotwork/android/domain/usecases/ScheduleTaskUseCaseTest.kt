package app.knotwork.android.domain.usecases

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import app.knotwork.android.data.services.AgentWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleTaskUseCaseTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduleTaskUseCase: ScheduleTaskUseCase

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
        scheduleTaskUseCase = ScheduleTaskUseCase(workManager)
    }

    @Test
    fun `invoke with interval greater than 0 schedules unique periodic work`() {
        val prompt = "check emails"
        val result = scheduleTaskUseCase(prompt, intervalHours = 2, delayMinutes = 0)

        val nameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        verify {
            workManager.enqueueUniquePeriodicWork(
                capture(nameSlot),
                capture(policySlot),
                any<PeriodicWorkRequest>(),
            )
        }
        assertTrue(nameSlot.captured.contains(prompt))
        assertTrue(nameSlot.captured.contains("2h"))
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
        assertTrue(result.contains("successfully scheduled"))
    }

    @Test
    fun `invoke with interval 0 schedules one-time work`() {
        val prompt = "check emails once"
        val result = scheduleTaskUseCase(prompt, intervalHours = 0, delayMinutes = 10)

        val slot = slot<OneTimeWorkRequest>()
        verify { workManager.enqueue(capture(slot)) }
        assertTrue(result.contains("successfully scheduled"))
    }

    @Test
    fun `invoke with session id carries it into one-time work input data`() {
        scheduleTaskUseCase("check emails once", sessionId = "session-7")

        val slot = slot<OneTimeWorkRequest>()
        verify { workManager.enqueue(capture(slot)) }
        assertEquals("session-7", slot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
        assertEquals("check emails once", slot.captured.workSpec.input.getString(AgentWorker.KEY_PROMPT))
    }

    @Test
    fun `invoke with session id carries it into periodic work input data`() {
        scheduleTaskUseCase("check emails", intervalHours = 2, sessionId = "session-7")

        val requestSlot = slot<PeriodicWorkRequest>()
        verify { workManager.enqueueUniquePeriodicWork(any(), any(), capture(requestSlot)) }
        assertEquals("session-7", requestSlot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
    }

    @Test
    fun `invoke without session id leaves the input data key absent`() {
        scheduleTaskUseCase("check emails once")

        val slot = slot<OneTimeWorkRequest>()
        verify { workManager.enqueue(capture(slot)) }
        assertNull(slot.captured.workSpec.input.getString(AgentWorker.KEY_SESSION_ID))
    }
}
