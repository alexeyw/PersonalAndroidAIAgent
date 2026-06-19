package app.knotwork.android.presentation.ui.models

import app.knotwork.android.domain.constants.TimeAndIdConstants
import app.knotwork.android.domain.usecases.BenchmarkReport
import java.util.Locale

/**
 * Display formatting for the model Performance card and the benchmark share
 * payload. Kept app-side (out of the catalog) so the design layer stays free of
 * unit/number-format logic, and pulled into a single file so the card readouts
 * and the shared text never drift.
 *
 * All formatting uses [Locale.US] for stable, comparable numerals (these are
 * engineering figures, not locale-sensitive prose).
 */
internal object PerformanceFormatting {

    private val MS_PER_SECOND = TimeAndIdConstants.MS_PER_SECOND.toDouble()
    private const val SECOND_THRESHOLD_MS = 1000L

    /**
     * Formats time-to-first-token: milliseconds below one second (`"420 ms"`),
     * seconds with one decimal at or above (`"1.2 s"`).
     */
    fun ttft(ms: Long): String = if (ms < SECOND_THRESHOLD_MS) {
        "$ms ms"
    } else {
        String.format(Locale.US, "%.1f s", ms / MS_PER_SECOND)
    }

    /** Formats decode throughput, one decimal place: `"12.4 tok/s"`. */
    fun decode(tokensPerSec: Float): String = String.format(Locale.US, "%.1f tok/s", tokensPerSec)

    /** Formats a total inference time in seconds, one decimal place: `"2.6 s"`. */
    fun totalSeconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / MS_PER_SECOND)

    /**
     * Formats a peak-memory byte count as `"1.8 GB"` at or above 1 GB, else
     * `"640 MB"`. Returns `null` when no reading is available (`bytes <= 0`), so
     * the card renders its "unavailable" dash.
     */
    fun memory(bytes: Long): String? = if (bytes <= 0L) null else gigabyteOrMegabyteLabel(bytes)

    /**
     * Builds the rolling-window caption shown on the populated card, e.g.
     * `"avg · last 8 runs"` (or `"avg · last 1 run"`).
     *
     * @param sampleCount Number of runs in the averaged window (`>= 1`).
     */
    fun sampleCaption(sampleCount: Int): String {
        val noun = if (sampleCount == 1) "run" else "runs"
        return "avg · last $sampleCount $noun"
    }

    /**
     * Builds the plain-text benchmark summary shared via the system share sheet.
     * Mirrors the designer's payload: model name, the four figures, and a
     * one-line method/approximation note.
     *
     * @param report The measured benchmark figures.
     * @return The multi-line shareable text.
     */
    fun shareText(report: BenchmarkReport): String {
        val peak = memory(report.peakNativeHeapBytes)
            ?.let { "$it (native heap, approx.)" }
            ?: "unavailable"
        return buildString {
            appendLine("${report.modelName} · benchmark")
            appendLine()
            appendLine("TTFT         ${ttft(report.ttftMs)}")
            appendLine("Decode       ${decode(report.decodeTokensPerSec)}")
            appendLine("Total        ${totalSeconds(report.totalMs)}")
            appendLine("Peak memory  $peak")
            appendLine()
            append("Measured on-device · 1 warm-up + 1 measured run.")
        }
    }
}
