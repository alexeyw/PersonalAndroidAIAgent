package app.knotwork.android.domain.engine

import app.knotwork.android.domain.constants.PerformanceConstants
import app.knotwork.android.domain.services.NativeMemorySampler

/**
 * Tracks the peak native-heap allocation observed across a single inference
 * window, sampling at a throttled cadence.
 *
 * Created fresh per generation (it holds mutable per-run state) and driven from
 * the token-collection loop: the caller invokes [observe] with the current
 * timestamp on every streamed token, and the sampler reads memory only when at
 * least [PerformanceConstants.MEMORY_SAMPLE_INTERVAL_MS] has elapsed since the
 * last read. This keeps the cost negligible (a few reads over a multi-second
 * generation) while still catching the peak, which on-device LLMs typically
 * reach mid- or late-generation as the KV cache grows.
 *
 * Shared by the LITE_RT node executor and the benchmark use case so both derive
 * the peak the same way.
 *
 * @property sampler Source of the current native-heap reading.
 * @property intervalMs Minimum spacing between two reads, in milliseconds.
 */
class PeakHeapSampler(
    private val sampler: NativeMemorySampler,
    private val intervalMs: Long = PerformanceConstants.MEMORY_SAMPLE_INTERVAL_MS,
) {
    private var hasSampled = false
    private var lastSampleAtMs = 0L

    /**
     * Largest native-heap reading seen so far, in bytes. `0` until the first
     * [observe] call takes a reading.
     */
    var peakBytes: Long = 0L
        private set

    /**
     * Records a throttled memory reading if enough time has passed since the
     * last one, updating [peakBytes]. Always takes a reading on the very first
     * call so a short generation still yields at least one sample.
     *
     * @param nowMs The current `System.currentTimeMillis()`.
     */
    fun observe(nowMs: Long) {
        if (hasSampled && nowMs - lastSampleAtMs < intervalMs) return
        hasSampled = true
        lastSampleAtMs = nowMs
        val reading = sampler.nativeHeapAllocatedBytes()
        if (reading > peakBytes) peakBytes = reading
    }
}
