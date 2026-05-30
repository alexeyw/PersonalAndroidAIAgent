package ai.agent.android.data.services

import ai.agent.android.domain.repositories.MemoryRepository
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagerMemoryReembedSchedulerTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val memoryRepository = mockk<MemoryRepository>()

    private fun scheduler() = WorkManagerMemoryReembedScheduler(workManager, memoryRepository)

    @Test
    fun `schedule enqueues a unique one-time re-embed work request that re-arms a fresh drain`() {
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingWorkPolicy>()
        every {
            workManager.enqueueUniqueWork(capture(nameSlot), capture(policySlot), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)

        scheduler().schedule()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
        assertEquals(MemoryReembedWorker.UNIQUE_NAME, nameSlot.captured)
        // APPEND_OR_REPLACE (not KEEP): a second import must chain a fresh pass
        // after the current one so its newly-flagged chunks are not stranded.
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policySlot.captured)
    }

    @Test
    fun `rearmIfPending enqueues a pass when chunks are still flagged`() = runTest {
        coEvery { memoryRepository.countMemoriesNeedingReembedding() } returns 3
        every {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)

        scheduler().rearmIfPending()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `rearmIfPending enqueues nothing when no chunks are pending`() = runTest {
        coEvery { memoryRepository.countMemoriesNeedingReembedding() } returns 0

        scheduler().rearmIfPending()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }
}
