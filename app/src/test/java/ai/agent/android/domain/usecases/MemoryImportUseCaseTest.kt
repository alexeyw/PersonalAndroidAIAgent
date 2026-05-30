package ai.agent.android.domain.usecases

import ai.agent.android.domain.memoryio.MemoryJsonSerializer
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemoryExportDocument
import ai.agent.android.domain.models.MemoryImportOutcome
import ai.agent.android.domain.models.MemoryImportStrategy
import ai.agent.android.domain.repositories.MemoryRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryImportUseCase] — the Merge / Replace strategies and the
 * provider-mismatch re-embedding flag.
 */
class MemoryImportUseCaseTest {

    private lateinit var repository: MemoryRepository
    private lateinit var useCase: MemoryImportUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = MemoryImportUseCase(repository)
        coEvery { repository.insertImportedMemories(any(), any()) } just Runs
        coEvery { repository.deleteAllMemories() } just Runs
    }

    private fun chunk(id: Long) = MemoryChunk(id = id, text = "t$id", embedding = floatArrayOf(0.1f), timestamp = id)

    private fun document(providerId: String, chunks: List<MemoryChunk>) =
        MemoryExportDocument(embeddingProviderId = providerId, exportedAt = 0L, chunks = chunks)

    @Test
    fun `parse delegates to the serializer`() {
        val doc = document("use", listOf(chunk(1)))
        val json = MemoryJsonSerializer.serialize(doc.chunks, "use", 0L)
        assertTrue(useCase.parse(json) is MemoryImportOutcome.Success)
    }

    @Test
    fun `Merge inserts only chunks whose id is not already present`() = runTest {
        coEvery { repository.getExistingMemoryIds() } returns setOf(1L, 2L)
        val captured = slot<List<MemoryChunk>>()
        coEvery { repository.insertImportedMemories(capture(captured), any()) } just Runs

        val doc = document("use", listOf(chunk(1), chunk(3)))
        val result = useCase.import(doc, MemoryImportStrategy.Merge, activeProviderId = "use")

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(listOf(3L), captured.captured.map { it.id })
        coVerify(exactly = 0) { repository.deleteAllMemories() }
    }

    @Test
    fun `Replace wipes the store then inserts every chunk`() = runTest {
        val captured = slot<List<MemoryChunk>>()
        coEvery { repository.insertImportedMemories(capture(captured), any()) } just Runs

        val doc = document("use", listOf(chunk(1), chunk(2)))
        val result = useCase.import(doc, MemoryImportStrategy.Replace, activeProviderId = "use")

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(listOf(1L, 2L), captured.captured.map { it.id })
        coVerify(exactly = 1) { repository.deleteAllMemories() }
    }

    @Test
    fun `needsReembedding is set only when the provider differs`() = runTest {
        val needsFlag = slot<Boolean>()
        coEvery { repository.insertImportedMemories(any(), capture(needsFlag)) } just Runs

        val mismatch = useCase.import(
            document("openai_3_small", listOf(chunk(1))),
            MemoryImportStrategy.Replace,
            activeProviderId = "use",
        )
        assertTrue(mismatch.needsReembedding)
        assertTrue(needsFlag.captured)

        val match = useCase.import(
            document("use", listOf(chunk(1))),
            MemoryImportStrategy.Replace,
            activeProviderId = "use",
        )
        assertFalse(match.needsReembedding)
        assertFalse(needsFlag.captured)
    }
}
