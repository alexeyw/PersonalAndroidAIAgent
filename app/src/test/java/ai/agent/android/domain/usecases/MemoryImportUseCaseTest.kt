package ai.agent.android.domain.usecases

import ai.agent.android.domain.memoryio.MemoryJsonSerializer
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemoryExportDocument
import ai.agent.android.domain.models.MemoryImportOutcome
import ai.agent.android.domain.models.MemoryImportStrategy
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.MemoryReembedScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryImportUseCase] — Merge / Replace strategies, the
 * provider-mismatch re-embed scheduling, and the empty-document Replace guard.
 */
class MemoryImportUseCaseTest {

    private lateinit var repository: MemoryRepository
    private lateinit var scheduler: MemoryReembedScheduler
    private lateinit var useCase: MemoryImportUseCase

    @Before
    fun setup() {
        repository = mockk()
        scheduler = mockk(relaxed = true)
        useCase = MemoryImportUseCase(repository, scheduler)
        coEvery { repository.insertImportedMemories(any(), any()) } just Runs
        coEvery { repository.replaceImportedMemories(any(), any()) } just Runs
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
        coVerify(exactly = 0) { repository.replaceImportedMemories(any(), any()) }
    }

    @Test
    fun `Merge always inserts id-less chunks`() = runTest {
        coEvery { repository.getExistingMemoryIds() } returns setOf(1L)
        val captured = slot<List<MemoryChunk>>()
        coEvery { repository.insertImportedMemories(capture(captured), any()) } just Runs

        // Two id-less (id == 0) chunks must both be inserted (Room auto-assigns
        // fresh keys); dedupById keeps every id-0 chunk rather than collapsing.
        val doc = document("use", listOf(chunk(0), chunk(0)))
        val result = useCase.import(doc, MemoryImportStrategy.Merge, activeProviderId = "use")

        assertEquals(2, result.imported)
        assertEquals(2, captured.captured.size)
    }

    @Test
    fun `Replace dedupes file-internal duplicate ids so the count matches stored rows`() = runTest {
        val captured = slot<List<MemoryChunk>>()
        coEvery { repository.replaceImportedMemories(capture(captured), any()) } just Runs

        // Two chunks share id=5; @Insert REPLACE would collapse them to one row,
        // so imported must report 1, not 2.
        val doc = document("use", listOf(chunk(5), chunk(5), chunk(6)))
        val result = useCase.import(doc, MemoryImportStrategy.Replace, activeProviderId = "use")

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(listOf(5L, 6L), captured.captured.map { it.id })
    }

    @Test
    fun `Replace transactionally replaces with every chunk`() = runTest {
        val captured = slot<List<MemoryChunk>>()
        coEvery { repository.replaceImportedMemories(capture(captured), any()) } just Runs

        val doc = document("use", listOf(chunk(1), chunk(2)))
        val result = useCase.import(doc, MemoryImportStrategy.Replace, activeProviderId = "use")

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(listOf(1L, 2L), captured.captured.map { it.id })
        coVerify(exactly = 1) { repository.replaceImportedMemories(any(), any()) }
    }

    @Test
    fun `Replace with an empty document leaves the store untouched`() = runTest {
        val doc = document("use", emptyList())

        val result = useCase.import(doc, MemoryImportStrategy.Replace, activeProviderId = "use")

        assertEquals(0, result.imported)
        coVerify(exactly = 0) { repository.replaceImportedMemories(any(), any()) }
        coVerify(exactly = 0) { repository.insertImportedMemories(any(), any()) }
        verify(exactly = 0) { scheduler.schedule() }
    }

    @Test
    fun `provider mismatch flags re-embedding and schedules the background pass`() = runTest {
        val needsFlag = slot<Boolean>()
        coEvery { repository.replaceImportedMemories(any(), capture(needsFlag)) } just Runs

        val result = useCase.import(
            document("openai_3_small", listOf(chunk(1))),
            MemoryImportStrategy.Replace,
            activeProviderId = "use",
        )

        assertTrue(result.needsReembedding)
        assertTrue(needsFlag.captured)
        verify(exactly = 1) { scheduler.schedule() }
    }

    @Test
    fun `matching provider does not schedule a re-embed`() = runTest {
        coEvery { repository.replaceImportedMemories(any(), capture(slot())) } just Runs

        val result = useCase.import(
            document("use", listOf(chunk(1))),
            MemoryImportStrategy.Replace,
            activeProviderId = "use",
        )

        assertFalse(result.needsReembedding)
        verify(exactly = 0) { scheduler.schedule() }
    }

    @Test
    fun `mismatch with nothing inserted does not schedule`() = runTest {
        coEvery { repository.getExistingMemoryIds() } returns setOf(1L)

        // All incoming chunks are duplicates, so nothing lands and no re-embed
        // is needed despite the provider mismatch.
        val result = useCase.import(
            document("openai_3_small", listOf(chunk(1))),
            MemoryImportStrategy.Merge,
            activeProviderId = "use",
        )

        assertEquals(0, result.imported)
        assertFalse(result.needsReembedding)
        verify(exactly = 0) { scheduler.schedule() }
    }
}
