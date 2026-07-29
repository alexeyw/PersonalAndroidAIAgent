package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.services.TaskScheduler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Unit tests for [CancelScheduledTasksUseCase] — the escape hatch from a task
 * that keeps re-scheduling itself.
 */
class CancelScheduledTasksUseCaseTest {

    private val taskScheduler = mockk<TaskScheduler>()
    private val useCase = CancelScheduledTasksUseCase(taskScheduler)

    @Test
    fun `when invoked then it cancels every scheduled task through the port`() {
        every { taskScheduler.cancelAllScheduled() } just Runs

        useCase()

        verify(exactly = 1) { taskScheduler.cancelAllScheduled() }
    }

    @Test
    fun `when invoked then it schedules nothing of its own`() {
        every { taskScheduler.cancelAllScheduled() } just Runs

        useCase()

        // A recovery action that re-armed anything would defeat its purpose.
        verify(exactly = 0) { taskScheduler.scheduleOneTime(any(), any(), any(), any()) }
        verify(exactly = 0) { taskScheduler.schedulePeriodic(any(), any(), any(), any()) }
    }
}
