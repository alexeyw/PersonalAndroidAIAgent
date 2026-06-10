package app.knotwork.android.data.services

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import app.knotwork.android.domain.models.MemoryStats
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryCompactionScheduler].
 *
 * WorkManager is mocked, so the tests assert the scheduler enqueues the right
 * kind of request under the right unique name / policy and that the hard-limit
 * watch fires only when the chunk count exceeds the configured ceiling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryCompactionSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduler: MemoryCompactionScheduler

    private fun stats(chunkCount: Int) = MemoryStats(
        chunkCount = chunkCount,
        totalBytes = 0L,
        threadCount = 0,
    )

    @Before
    fun setup() {
        workManager = mockk(relaxed = true)
        memoryRepository = mockk()
        settingsRepository = mockk()
        scheduler = MemoryCompactionScheduler(workManager, memoryRepository, settingsRepository)
    }

    @Test
    fun `schedulePeriodic enqueues a unique periodic work kept on re-schedule`() {
        val request = slot<PeriodicWorkRequest>()

        scheduler.schedulePeriodic()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                MemoryCompactionWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                capture(request),
            )
        }
        assertTrue(request.isCaptured)
    }

    @Test
    fun `triggerImmediate enqueues a unique one-time work kept on burst`() {
        val request = slot<OneTimeWorkRequest>()

        scheduler.triggerImmediate()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                capture(request),
            )
        }
        assertTrue(request.isCaptured)
    }

    @Test
    fun `startHardLimitWatch triggers immediate compaction when over the limit`() = runTest {
        every { memoryRepository.observeStats() } returns flowOf(stats(chunkCount = 6_000))
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startHardLimitWatch survives a failing stats flow and can be re-armed`() = runTest {
        // The stats flow is the first DB access on an app-lifetime scope with no exception
        // handler: an uncaught throw (e.g. SQLCipher passphrase unavailable) would crash the
        // process before the splash recovery screen renders. The watch must absorb the
        // failure and release its idempotency guard so a later call starts a fresh collector.
        var emissions = 0
        every { memoryRepository.observeStats() } answers {
            emissions++
            if (emissions == 1) {
                kotlinx.coroutines.flow.flow { throw IllegalStateException("database unavailable") }
            } else {
                flowOf(stats(chunkCount = 6_000))
            }
        }
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        // First collector died quietly; the guard is released, so a re-arm works.
        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startHardLimitWatch does not trigger when within the limit`() = runTest {
        every { memoryRepository.observeStats() } returns flowOf(stats(chunkCount = 100))
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `startHardLimitWatch re-arms when the count climbs further over the limit`() = runTest {
        // The first immediate run could not drop below the ceiling; a later
        // insert that bumps the count must enqueue another pass.
        every { memoryRepository.observeStats() } returns
            flowOf(stats(chunkCount = 6_000), stats(chunkCount = 7_000))
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        verify(exactly = 2) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startHardLimitWatch does not re-trigger on an unchanged over-limit count`() = runTest {
        // A stuck-over-limit state that never changes must not loop.
        every { memoryRepository.observeStats() } returns
            flowOf(stats(chunkCount = 6_000), stats(chunkCount = 6_000))
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startHardLimitWatch is idempotent across repeated calls`() = runTest {
        every { memoryRepository.observeStats() } returns flowOf(stats(chunkCount = 6_000))
        every { settingsRepository.maxMemoryChunks } returns flowOf(5_000)
        scheduler.watchScope = this

        scheduler.startHardLimitWatch()
        scheduler.startHardLimitWatch()
        advanceUntilIdle()

        // The second call must register no extra collector — a single trigger.
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
