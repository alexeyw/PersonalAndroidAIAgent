package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.ModelPerformanceDao
import app.knotwork.android.data.local.models.ModelPerformanceSampleEntity
import app.knotwork.android.domain.constants.PerformanceConstants
import app.knotwork.android.domain.models.ModelPerformanceSample
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPerformanceRepositoryImplTest {

    private val dao: ModelPerformanceDao = mockk(relaxed = true)
    private val repository = ModelPerformanceRepositoryImpl(dao)

    @Test
    fun `given a sample when recorded then it is inserted and trimmed to the retention cap`() = runTest {
        val sample = sample()
        val slot = slot<ModelPerformanceSampleEntity>()
        coEvery { dao.insertAndTrim(capture(slot), any()) } returns Unit

        repository.record(sample)

        coVerify { dao.insertAndTrim(any(), PerformanceConstants.RETENTION_PER_MODEL) }
        assertEquals("/m.litertlm", slot.captured.modelPath)
        assertEquals(true, slot.captured.isBenchmark)
    }

    @Test
    fun `given the dao throws when recording then the failure is swallowed`() = runTest {
        coEvery { dao.insertAndTrim(any(), any()) } throws IllegalStateException("disk full")

        // Must not propagate — a metrics write may never break the run.
        repository.record(sample())
    }

    @Test
    fun `given a model path when deleteForModel then the dao drops its samples`() = runTest {
        repository.deleteForModel("/m.litertlm")

        coVerify { dao.deleteForModel("/m.litertlm") }
    }

    @Test
    fun `given the dao throws when deleting then the failure is swallowed`() = runTest {
        coEvery { dao.deleteForModel(any()) } throws IllegalStateException("locked")

        // Must not propagate — failing to drop samples may never break model deletion.
        repository.deleteForModel("/m.litertlm")
    }

    @Test
    fun `given stored rows when observed then they are mapped to domain newest-first`() = runTest {
        every { dao.observeRecentForModel("/m.litertlm", 8) } returns flowOf(
            listOf(entity(id = 2), entity(id = 1)),
        )

        val result = repository.observeRecentForModel("/m.litertlm", 8).first()

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    private fun sample(): ModelPerformanceSample = ModelPerformanceSample(
        modelPath = "/m.litertlm",
        ttftMs = 400,
        decodeTokensPerSec = 12f,
        totalMs = 1_000,
        tokenCount = 10,
        peakNativeHeapBytes = 2_048,
        isBenchmark = true,
        createdAt = 123,
    )

    private fun entity(id: Long): ModelPerformanceSampleEntity = ModelPerformanceSampleEntity(
        id = id,
        modelPath = "/m.litertlm",
        ttftMs = 400,
        decodeTokensPerSec = 12f,
        totalMs = 1_000,
        tokenCount = 10,
        peakNativeHeapBytes = 2_048,
        isBenchmark = false,
        createdAt = 123,
    )
}
