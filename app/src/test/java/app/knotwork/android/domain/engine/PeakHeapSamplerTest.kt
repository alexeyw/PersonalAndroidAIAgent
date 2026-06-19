package app.knotwork.android.domain.engine

import app.knotwork.android.domain.services.NativeMemorySampler
import org.junit.Assert.assertEquals
import org.junit.Test

class PeakHeapSamplerTest {

    @Test
    fun `given readings when observed then keeps the maximum`() {
        val readings = ArrayDeque(listOf(100L, 500L, 300L))
        val sampler = PeakHeapSampler(NativeMemorySampler { readings.removeFirst() }, intervalMs = 0)

        sampler.observe(0)
        sampler.observe(10)
        sampler.observe(20)

        assertEquals(500L, sampler.peakBytes)
    }

    @Test
    fun `given reads within the interval when observed then they are throttled`() {
        var calls = 0
        val sampler = PeakHeapSampler(
            NativeMemorySampler {
                calls += 1
                calls.toLong()
            },
            intervalMs = 150,
        )

        sampler.observe(0) // first read always taken
        sampler.observe(50) // throttled
        sampler.observe(100) // throttled
        sampler.observe(200) // 200 - 0 >= 150 → taken

        assertEquals(2, calls)
    }

    @Test
    fun `given no observations when read then peak is zero`() {
        val sampler = PeakHeapSampler(NativeMemorySampler { 999L })
        assertEquals(0L, sampler.peakBytes)
    }
}
