package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.TaskScheduler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleTaskUseCaseTest {

    private lateinit var taskScheduler: TaskScheduler
    private lateinit var scheduleTaskUseCase: ScheduleTaskUseCase

    @Before
    fun setup() {
        taskScheduler = mockk()
        every { taskScheduler.scheduleOneTime(any(), any(), any(), any()) } just Runs
        every { taskScheduler.schedulePeriodic(any(), any(), any(), any()) } just Runs
        scheduleTaskUseCase = ScheduleTaskUseCase(taskScheduler)
    }

    @Test
    fun `given positive interval when invoked then delegates to periodic scheduling`() {
        val result = scheduleTaskUseCase("check emails", intervalHours = 2, delayMinutes = 0)

        verify {
            taskScheduler.schedulePeriodic(
                "check emails",
                2L,
                null,
                ScheduledTaskConstraints(requiresBatteryNotLow = true),
            )
        }
        verify(exactly = 0) { taskScheduler.scheduleOneTime(any(), any(), any(), any()) }
        assertTrue(result.contains("every 2 hours"))
    }

    @Test
    fun `given zero interval when invoked then delegates to one-time scheduling`() {
        val result = scheduleTaskUseCase("check emails once", intervalHours = 0, delayMinutes = 10)

        verify {
            taskScheduler.scheduleOneTime(
                "check emails once",
                10L,
                null,
                ScheduledTaskConstraints(requiresBatteryNotLow = true),
            )
        }
        verify(exactly = 0) { taskScheduler.schedulePeriodic(any(), any(), any(), any()) }
        assertTrue(result.contains("10 minutes delay"))
    }

    @Test
    fun `given session id when one-time then forwards session id to port`() {
        val sessionSlot = slot<String?>()
        every { taskScheduler.scheduleOneTime(any(), any(), captureNullable(sessionSlot), any()) } just Runs

        scheduleTaskUseCase("check emails once", sessionId = "session-7")

        assertEquals("session-7", sessionSlot.captured)
    }

    @Test
    fun `given session id when periodic then forwards session id to port`() {
        val sessionSlot = slot<String?>()
        every { taskScheduler.schedulePeriodic(any(), any(), captureNullable(sessionSlot), any()) } just Runs

        scheduleTaskUseCase("check emails", intervalHours = 2, sessionId = "session-7")

        assertEquals("session-7", sessionSlot.captured)
    }

    @Test
    fun `given no session id when invoked then forwards null to port`() {
        val sessionSlot = slot<String?>()
        every { taskScheduler.scheduleOneTime(any(), any(), captureNullable(sessionSlot), any()) } just Runs

        scheduleTaskUseCase("check emails once")

        assertNull(sessionSlot.captured)
    }

    @Test
    fun `given scheduler throws when invoked then returns failure message`() {
        every {
            taskScheduler.scheduleOneTime(any(), any(), any(), any())
        } throws IllegalStateException("queue full")

        val result = scheduleTaskUseCase("check emails once")

        assertTrue(result.startsWith("Failed to schedule task:"))
        assertTrue(result.contains("queue full"))
    }
}
