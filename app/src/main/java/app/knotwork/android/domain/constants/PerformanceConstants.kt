package app.knotwork.android.domain.constants

/**
 * Tunables for the on-device performance-insights feature (per-model TTFT,
 * decode speed and peak native memory, plus the controlled benchmark).
 *
 * These are deliberately code-level constants rather than user settings: the
 * design surfaces a single rolling-average readout with no knobs, so exposing
 * the window size or sampling cadence would add configuration the UI never
 * shows.
 */
object PerformanceConstants {

    /**
     * Number of most-recent runs averaged into the model's Performance card.
     * Matches the "avg · last N runs" caption on the card. Older samples stay
     * in the table but fall outside the rolling window.
     */
    const val SAMPLE_WINDOW: Int = 8

    /**
     * Minimum spacing, in milliseconds, between two peak-native-memory reads
     * during a single inference window. Native-heap reads are cheap (unlike the
     * rate-throttled `Debug.getMemoryInfo()`/PSS path), but sampling on every
     * streamed token is needless work — once every ~150 ms over a multi-second
     * generation captures the peak without measurable overhead.
     */
    const val MEMORY_SAMPLE_INTERVAL_MS: Long = 150L
}
