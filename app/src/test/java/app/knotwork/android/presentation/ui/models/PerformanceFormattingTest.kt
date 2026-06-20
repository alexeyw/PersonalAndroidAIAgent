package app.knotwork.android.presentation.ui.models

import app.knotwork.android.domain.usecases.BenchmarkReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceFormattingTest {

    @Test
    fun `ttft renders milliseconds below one second and seconds above`() {
        assertEquals("420 ms", PerformanceFormatting.ttft(420))
        assertEquals("999 ms", PerformanceFormatting.ttft(999))
        assertEquals("1.2 s", PerformanceFormatting.ttft(1_200))
    }

    @Test
    fun `decode renders one decimal place`() {
        assertEquals("12.4 tok/s", PerformanceFormatting.decode(12.44f))
    }

    @Test
    fun `total renders seconds with one decimal`() {
        assertEquals("2.6 s", PerformanceFormatting.totalSeconds(2_550))
    }

    @Test
    fun `memory renders GB above one gigabyte and MB below`() {
        assertEquals("1.8 GB", PerformanceFormatting.memory((1.8 * 1024 * 1024 * 1024).toLong()))
        assertEquals("640 MB", PerformanceFormatting.memory(640L * 1024 * 1024))
    }

    @Test
    fun `memory is null when no reading is available`() {
        assertNull(PerformanceFormatting.memory(0))
        assertNull(PerformanceFormatting.memory(-1))
    }

    @Test
    fun `memory never renders 1024 MB just below the gigabyte boundary`() {
        // ~1023.9 MiB: would round to "1024 MB" under a naive %.0f; must read as GB instead.
        val justUnderOneGb = (1023.9 * 1024 * 1024).toLong()
        assertEquals("1.0 GB", PerformanceFormatting.memory(justUnderOneGb))
    }

    @Test
    fun `sample caption pluralises the run noun`() {
        assertEquals("avg · last 1 run", PerformanceFormatting.sampleCaption(1))
        assertEquals("avg · last 8 runs", PerformanceFormatting.sampleCaption(8))
    }

    @Test
    fun `share text carries the model name the four figures and the approx note`() {
        val report = BenchmarkReport(
            modelName = "gemma-4-E2B-it",
            ttftMs = 390,
            decodeTokensPerSec = 13.1f,
            totalMs = 2_600,
            peakNativeHeapBytes = (1.8 * 1024 * 1024 * 1024).toLong(),
        )

        val text = PerformanceFormatting.shareText(report)

        assertTrue(text.contains("gemma-4-E2B-it · benchmark"))
        assertTrue(text.contains("TTFT         390 ms"))
        assertTrue(text.contains("Decode       13.1 tok/s"))
        assertTrue(text.contains("Total        2.6 s"))
        assertTrue(text.contains("Peak memory  1.8 GB (native heap, approx.)"))
        assertTrue(text.contains("1 warm-up + 1 measured run"))
    }

    @Test
    fun `share text marks memory unavailable when no reading was taken`() {
        val report = BenchmarkReport("m", 100, 5f, 1_000, 0)
        assertTrue(PerformanceFormatting.shareText(report).contains("Peak memory  unavailable"))
    }
}
