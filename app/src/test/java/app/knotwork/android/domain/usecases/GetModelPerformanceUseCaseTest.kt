package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.PerformanceConstants
import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetModelPerformanceUseCaseTest {

    private val repository: ModelPerformanceRepository = mockk()
    private val useCase = GetModelPerformanceUseCase(repository)

    @Test
    fun `given samples when invoked then emits the folded summary`() = runTest {
        every { repository.observeRecentForModel("/m.litertlm", PerformanceConstants.SAMPLE_WINDOW) } returns
            flowOf(listOf(sample(ttftMs = 200), sample(ttftMs = 400)))

        val summary = useCase("/m.litertlm").first()

        assertEquals(2, summary!!.sampleCount)
        assertEquals(300, summary.avgTtftMs)
    }

    @Test
    fun `given no samples when invoked then emits null`() = runTest {
        every { repository.observeRecentForModel(any(), any()) } returns flowOf(emptyList())

        assertNull(useCase("/m.litertlm").first())
    }

    @Test
    fun `given invoked then queries the rolling window size`() = runTest {
        every { repository.observeRecentForModel(any(), any()) } returns flowOf(emptyList())

        useCase("/m.litertlm").first()

        verify { repository.observeRecentForModel("/m.litertlm", PerformanceConstants.SAMPLE_WINDOW) }
    }

    private fun sample(ttftMs: Long): ModelPerformanceSample = ModelPerformanceSample(
        modelPath = "/m.litertlm",
        ttftMs = ttftMs,
        decodeTokensPerSec = 10f,
        totalMs = 1_000,
        tokenCount = 10,
        peakNativeHeapBytes = 1_000,
        isBenchmark = false,
        createdAt = 0,
    )
}
