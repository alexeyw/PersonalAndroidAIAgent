package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelPerformanceSampleTest {

    @Test
    fun `given timings when fromTimings then TTFT excludes load and decode spans first to last token`() {
        // inferenceStart=1000, first token at 1400 → TTFT 400 ms.
        // last token at 2000 → decode window 600 ms over (10-1)=9 intervals → 15 tok/s.
        val sample = ModelPerformanceSample.fromTimings(
            modelPath = "/m.litertlm",
            inferenceStartMs = 1_000,
            firstTokenAtMs = 1_400,
            endMs = 2_000,
            tokenCount = 10,
            peakNativeHeapBytes = 123,
            isBenchmark = false,
            createdAt = 2_000,
        )

        assertEquals(400, sample.ttftMs)
        assertEquals(1_000, sample.totalMs)
        assertEquals(15.0f, sample.decodeTokensPerSec, 0.001f)
        assertEquals(123, sample.peakNativeHeapBytes)
    }

    @Test
    fun `given no tokens when fromTimings then TTFT and decode are zero`() {
        val sample = ModelPerformanceSample.fromTimings(
            modelPath = "/m.litertlm",
            inferenceStartMs = 1_000,
            firstTokenAtMs = 0,
            endMs = 1_500,
            tokenCount = 0,
            peakNativeHeapBytes = 0,
            isBenchmark = true,
            createdAt = 1_500,
        )

        assertEquals(0, sample.ttftMs)
        assertEquals(0f, sample.decodeTokensPerSec, 0.0f)
        assertEquals(500, sample.totalMs)
    }

    @Test
    fun `given a single token when fromTimings then decode is zero but TTFT is measured`() {
        val sample = ModelPerformanceSample.fromTimings(
            modelPath = "/m.litertlm",
            inferenceStartMs = 0,
            firstTokenAtMs = 250,
            endMs = 250,
            tokenCount = 1,
            peakNativeHeapBytes = 10,
            isBenchmark = false,
            createdAt = 250,
        )

        assertEquals(250, sample.ttftMs)
        assertEquals(0f, sample.decodeTokensPerSec, 0.0f)
    }

    @Test
    fun `given empty samples when summary from then null`() {
        assertNull(ModelPerformanceSummary.from(emptyList()))
    }

    @Test
    fun `given samples when summary from then averages TTFT and decode and takes the worst-case peak`() {
        val samples = listOf(
            sample(ttftMs = 400, decode = 10f, peak = 1_000),
            sample(ttftMs = 600, decode = 20f, peak = 3_000),
            sample(ttftMs = 200, decode = 30f, peak = 2_000),
        )

        val summary = ModelPerformanceSummary.from(samples)!!

        assertEquals(3, summary.sampleCount)
        assertEquals(400, summary.avgTtftMs) // (400+600+200)/3
        assertEquals(20f, summary.avgDecodeTokensPerSec, 0.001f) // (10+20+30)/3
        assertEquals(3_000, summary.peakNativeHeapBytes) // max, not average
    }

    private fun sample(ttftMs: Long, decode: Float, peak: Long): ModelPerformanceSample = ModelPerformanceSample(
        modelPath = "/m.litertlm",
        ttftMs = ttftMs,
        decodeTokensPerSec = decode,
        totalMs = 1_000,
        tokenCount = 10,
        peakNativeHeapBytes = peak,
        isBenchmark = false,
        createdAt = 0,
    )
}
