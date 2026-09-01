package app.knotwork.android.presentation.ui.settings

import kotlin.math.roundToLong

/**
 * Unit conversions for the five tool / workspace ceilings.
 *
 * The settings are stored in the unit their consumers use — milliseconds for
 * the tool-call deadline, bytes for the three size caps — while the sliders are
 * shown in the unit a person reads: seconds, megabytes, kilobytes. (The read
 * budget is already in tokens and needs no conversion.) That leaves a conversion
 * on the way in and its inverse on the way out, and the pair has to agree or a
 * slider silently rewrites the value it was only meant to display.
 *
 * Both halves live here rather than at the two call sites (`buildToolsViewState`
 * and `routeToolsSlider`) precisely so the agreement is one testable object and
 * not a constant copied into two files.
 */
internal object ToolCeilingUnits {

    /** Milliseconds per second. */
    private const val MILLIS_PER_SECOND = 1_000L

    /** Bytes per kilobyte. */
    private const val BYTES_PER_KB = 1024L

    /** Bytes per megabyte. */
    private const val BYTES_PER_MB = 1024L * 1024

    /**
     * Renders a millisecond duration as whole seconds for a slider position.
     *
     * @param millis The stored duration.
     * @return The same duration in seconds.
     */
    fun toSeconds(millis: Long): Int = (millis / MILLIS_PER_SECOND).toInt()

    /**
     * Converts a slider's seconds position back into stored milliseconds.
     *
     * @param seconds The dragged position.
     * @return The duration to persist.
     */
    fun secondsToMillis(seconds: Float): Long = seconds.roundToLong() * MILLIS_PER_SECOND

    /**
     * Renders a byte count as whole megabytes for a slider position.
     *
     * @param bytes The stored ceiling.
     * @return The same ceiling in megabytes.
     */
    fun toMegabytes(bytes: Long): Int = (bytes / BYTES_PER_MB).toInt()

    /**
     * Converts a slider's megabyte position back into stored bytes.
     *
     * @param megabytes The dragged position.
     * @return The ceiling to persist.
     */
    fun megabytesToBytes(megabytes: Float): Long = megabytes.roundToLong() * BYTES_PER_MB

    /**
     * Renders a byte count as whole kilobytes for a slider position.
     *
     * @param bytes The stored ceiling.
     * @return The same ceiling in kilobytes.
     */
    fun toKilobytes(bytes: Long): Int = (bytes / BYTES_PER_KB).toInt()

    /**
     * Converts a slider's kilobyte position back into stored bytes.
     *
     * @param kilobytes The dragged position.
     * @return The ceiling to persist.
     */
    fun kilobytesToBytes(kilobytes: Float): Long = kilobytes.roundToLong() * BYTES_PER_KB
}
