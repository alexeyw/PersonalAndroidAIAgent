package app.knotwork.android.domain.models

/**
 * Outcome of the "Test backend" action inside Settings → Local model. The
 * persisted record powers the row's subtitle ("Last probe · 248 tok in
 * 1.42 s · 174 tok/s") so the user sees the most recent measured throughput
 * even after returning to the screen later.
 *
 * @property tokensGenerated Number of tokens the probe generated before
 *   the fixed prompt terminated.
 * @property durationMs Wall-clock duration of the probe in milliseconds.
 * @property timestampMs `System.currentTimeMillis()` when the probe
 *   finished — used to display a relative "ran 2 minutes ago" hint when
 *   the value goes stale.
 * @property success `true` when the underlying backend returned a token
 *   stream; `false` when the load / generate path errored. Errors persist
 *   the result so the user can see why the last attempt failed without
 *   re-running.
 * @property errorMessage Free-form description shown beneath the row when
 *   [success] is `false`. `null` on success.
 */
data class TestProbeResult(
    val tokensGenerated: Int,
    val durationMs: Long,
    val timestampMs: Long,
    val success: Boolean,
    val errorMessage: String? = null,
) {
    /** Throughput rounded to one decimal place. Zero for zero-duration probes. */
    val tokensPerSecond: Float
        get() = if (durationMs <= 0) 0f else tokensGenerated * MS_PER_SECOND_F / durationMs

    /** Numeric constants used by the throughput helper. */
    companion object {
        private const val MS_PER_SECOND_F: Float = 1_000f
    }
}
