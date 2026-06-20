package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.services.NativeMemorySampler

/**
 * Instruments a single streamed on-device generation so the LITE_RT node
 * executor and the benchmark derive their performance figures the same way.
 *
 * Created fresh per generation, immediately before stream consumption begins
 * (after the model is loaded): construction stamps [inferenceStartMs] as the
 * time-to-first-token baseline. The caller invokes [onToken] for every streamed
 * token; the meter records the first-token timestamp, counts tokens, and drives
 * a throttled [PeakHeapSampler]. When the stream completes, [toSample] folds the
 * captured timings into a [ModelPerformanceSample].
 *
 * This keeps the *instrumentation* shared (not just the arithmetic in
 * [ModelPerformanceSample.fromTimings]), so a change to how a run is measured
 * stays consistent between real runs and the benchmark.
 *
 * @param nativeMemorySampler source of native-heap readings for the peak.
 */
class StreamInferenceMeter(nativeMemorySampler: NativeMemorySampler) {

    private val peakHeapSampler = PeakHeapSampler(nativeMemorySampler)

    /**
     * `System.currentTimeMillis()` captured at construction — the start of
     * stream consumption, used as the time-to-first-token baseline.
     */
    val inferenceStartMs: Long = System.currentTimeMillis()

    /** Timestamp of the first observed token, or `0` until one arrives. */
    var firstTokenAtMs: Long = 0L
        private set

    /** Number of tokens observed so far. */
    var tokenCount: Int = 0
        private set

    /**
     * Records one streamed token: stamps the first-token time, increments the
     * count, and takes a throttled peak-memory reading.
     *
     * @param nowMs The current `System.currentTimeMillis()`.
     */
    fun onToken(nowMs: Long) {
        if (firstTokenAtMs == 0L) firstTokenAtMs = nowMs
        tokenCount += 1
        peakHeapSampler.observe(nowMs)
    }

    /**
     * Folds the captured timings into a sample.
     *
     * @param modelPath Concrete on-disk path of the model that ran.
     * @param endMs `System.currentTimeMillis()` after the stream completed.
     * @param isBenchmark Whether this run was the controlled benchmark.
     * @param createdAt Epoch-millis to stamp the sample with.
     * @return The computed [ModelPerformanceSample].
     */
    fun toSample(modelPath: String, endMs: Long, isBenchmark: Boolean, createdAt: Long): ModelPerformanceSample =
        ModelPerformanceSample.fromTimings(
            modelPath = modelPath,
            inferenceStartMs = inferenceStartMs,
            firstTokenAtMs = firstTokenAtMs,
            endMs = endMs,
            tokenCount = tokenCount,
            peakNativeHeapBytes = peakHeapSampler.peakBytes,
            isBenchmark = isBenchmark,
            createdAt = createdAt,
        )
}
