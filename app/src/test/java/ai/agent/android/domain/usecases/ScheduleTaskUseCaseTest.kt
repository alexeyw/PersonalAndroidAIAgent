package ai.agent.android.domain.usecases

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
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
}
