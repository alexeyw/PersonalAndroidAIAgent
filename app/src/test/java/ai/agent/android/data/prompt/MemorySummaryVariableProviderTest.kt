package ai.agent.android.data.prompt

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
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
    fun `given memories within limit when resolve then renders numbered list newest first`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(5)
        coEvery { memoryRepository.getAllMemories() } returns listOf(
            chunk(id = 1L, text = "older", timestamp = 100L),
            chunk(id = 2L, text = "middle", timestamp = 200L),
            chunk(id = 3L, text = "newest", timestamp = 300L),
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
    fun `given more memories than limit when resolve then truncates to configured size`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(2)
        coEvery { memoryRepository.getAllMemories() } returns listOf(
            chunk(id = 1L, text = "a", timestamp = 100L),
            chunk(id = 2L, text = "b", timestamp = 200L),
            chunk(id = 3L, text = "c", timestamp = 300L),
            chunk(id = 4L, text = "d", timestamp = 400L),
        )

        val result = provider.resolve()

        assertEquals(
            "1. d\n" +
                "2. c",
            result,
        )
    }

    @Test
    fun `given empty memories when resolve then returns empty string`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(5)
        coEvery { memoryRepository.getAllMemories() } returns emptyList()

        val result = provider.resolve()

        assertEquals("", result)
    }

    @Test
    fun `given non positive limit when resolve then returns empty string without reading memory`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(0)

        val result = provider.resolve()

        assertEquals("", result)
        // memoryRepository must NOT have been touched: the disabled-by-config short-circuit
        // is the contract that lets users turn the variable off without paying for an I/O hop.
        coVerify(exactly = 0) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `given memories already sorted ascending when resolve then re-sorts to descending`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns flowOf(3)
        coEvery { memoryRepository.getAllMemories() } returns listOf(
            chunk(id = 1L, text = "first", timestamp = 100L),
            chunk(id = 2L, text = "second", timestamp = 200L),
            chunk(id = 3L, text = "third", timestamp = 300L),
        )

        val result = provider.resolve()

        // Newest-first ordering is part of the contract regardless of the
        // order the repository returns rows in.
        assertEquals(
            "1. third\n" +
                "2. second\n" +
                "3. first",
            result,
        )
    }

    private fun chunk(id: Long, text: String, timestamp: Long): MemoryChunk =
        MemoryChunk(id = id, text = text, embedding = FloatArray(0), timestamp = timestamp)
}
