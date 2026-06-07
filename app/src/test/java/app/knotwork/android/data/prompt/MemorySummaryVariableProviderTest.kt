package app.knotwork.android.data.prompt

import app.knotwork.android.domain.models.MemorySummary
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MemorySummaryVariableProvider].
 */
class MemorySummaryVariableProviderTest {

    private val memoryRepository: MemoryRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val provider = MemorySummaryVariableProvider(memoryRepository, settingsRepository)

    @Test
    fun `given key when called then returns MEMORY_SUMMARY`() {
        assertEquals("MEMORY_SUMMARY", provider.key())
    }

    @Test
    fun `given recent summaries when resolve then renders numbered list newest first`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(5)
        // Repository contract: results already ordered DESC by timestamp (DAO does the sort).
        coEvery { memoryRepository.getRecentMemorySummaries(5) } returns listOf(
            summary(id = 3L, text = "newest", timestamp = 300L),
            summary(id = 2L, text = "middle", timestamp = 200L),
            summary(id = 1L, text = "older", timestamp = 100L),
        )

        val result = provider.resolve()

        assertEquals(
            "1. newest\n" +
                "2. middle\n" +
                "3. older",
            result,
        )
    }

    @Test
    fun `given limit configured when resolve then forwards exact limit to repository`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(2)
        coEvery { memoryRepository.getRecentMemorySummaries(2) } returns listOf(
            summary(id = 4L, text = "d", timestamp = 400L),
            summary(id = 3L, text = "c", timestamp = 300L),
        )

        val result = provider.resolve()

        // The provider must NOT load every memory and truncate locally — it must
        // ask the repository for exactly `limit` rows so embeddings of older
        // memories never get loaded.
        coVerify(exactly = 1) { memoryRepository.getRecentMemorySummaries(2) }
        coVerify(exactly = 0) { memoryRepository.getAllMemories() }
        assertEquals(
            "1. d\n" +
                "2. c",
            result,
        )
    }

    @Test
    fun `given empty memories when resolve then returns empty string`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(5)
        coEvery { memoryRepository.getRecentMemorySummaries(5) } returns emptyList()

        val result = provider.resolve()

        assertEquals("", result)
    }

    @Test
    fun `given non positive limit when resolve then returns empty without touching repository`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(0)

        val result = provider.resolve()

        assertEquals("", result)
        // Disabled-by-config short-circuit: no repository access at all.
        coVerify(exactly = 0) { memoryRepository.getRecentMemorySummaries(any()) }
        coVerify(exactly = 0) { memoryRepository.getAllMemories() }
    }

    private fun summary(id: Long, text: String, timestamp: Long): MemorySummary =
        MemorySummary(id = id, text = text, timestamp = timestamp)
}
