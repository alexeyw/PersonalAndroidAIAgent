package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EstimateCompactionUseCase].
 */
class EstimateCompactionUseCaseTest {

    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: EstimateCompactionUseCase

    private val now = 1_000_000_000_000L

    private fun chunk(id: Long) =
        MemoryChunk(id = id, text = "x".repeat(10), embedding = floatArrayOf(0f, 0f), timestamp = 1L)

    @Before
    fun setup() {
        memoryRepository = mockk()
        settingsRepository = mockk()
        every { settingsRepository.memoryCompactionAgeDays } returns flowOf(30)
        useCase = EstimateCompactionUseCase(memoryRepository, settingsRepository)
    }

    @Test
    fun `given fewer than three candidates returns empty estimate`() = runTest {
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns listOf(chunk(1), chunk(2))

        val estimate = useCase(now)

        assertEquals(CompactionEstimate.EMPTY, estimate)
    }

    @Test
    fun `given a candidate set estimates removed freed and runtime`() = runTest {
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns (1L..9L).map { chunk(it) }

        val estimate = useCase(now)

        // N=9 -> clusters = max(1, floor(sqrt(9)/2)) = 1; removed = 9 - 1 = 8.
        assertEquals(8, estimate.estimatedRemoved)
        assertTrue("freed bytes positive", estimate.estimatedFreedBytes > 0)
        assertTrue("runtime positive", estimate.estimatedRuntimeSeconds >= 1)
    }
}
