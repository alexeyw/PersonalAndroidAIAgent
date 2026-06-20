package app.knotwork.android.domain.services

import kotlinx.coroutines.flow.StateFlow

/**
 * Captures a short voice clip for transcription.
 *
 * The recorder writes a canonical **16 kHz mono 16-bit PCM WAV** into the
 * ephemeral [AudioCaptureStore] and auto-stops at a caller-supplied limit. It is
 * a single-shot, single-active-capture component: [start] begins a capture,
 * [stop] finalizes it, [cancel] aborts and discards it. Progress is published
 * through [state] so the composer can show a live timer.
 */
interface AudioRecorder {
    /** Live capture state, observed by the chat composer to drive the recording bar. */
    val state: StateFlow<RecordingState>

    /**
     * Starts capturing into a fresh clip. Emits [RecordingState.Recording] with a
     * ticking elapsed time, and transitions to [RecordingState.Finished] on its
     * own once [maxDurationSec] is reached (auto-stop). A no-op when a capture is
     * already in flight.
     *
     * @param maxDurationSec the recording limit, after which capture auto-stops.
     */
    suspend fun start(maxDurationSec: Int)

    /**
     * Stops the in-flight capture and finalizes the WAV. After this the [state] is
     * [RecordingState.Finished] carrying the clip path (also returned here).
     *
     * @return the absolute path of the finalized clip, or `null` when nothing was
     *   captured (e.g. [stop] called while idle, or the capture failed).
     */
    suspend fun stop(): String?

    /**
     * Aborts the in-flight capture and deletes its partial clip, returning to
     * [RecordingState.Idle]. Safe to call when already idle.
     */
    fun cancel()
}

/**
 * State of the single [AudioRecorder] capture.
 */
sealed interface RecordingState {
    /** No capture in progress. */
    data object Idle : RecordingState

    /**
     * A capture is in progress.
     *
     * @property elapsedSec whole seconds captured so far.
     * @property maxSec the configured recording limit.
     */
    data class Recording(val elapsedSec: Int, val maxSec: Int) : RecordingState

    /**
     * Capture finished (manual stop or auto-stop at the limit); the clip is ready
     * to transcribe.
     *
     * @property path absolute path of the finalized WAV clip.
     */
    data class Finished(val path: String) : RecordingState

    /**
     * Capture failed and produced no usable clip (the mic could not be opened or
     * an I/O error interrupted recording). Terminal, like [Finished], so an
     * observer can distinguish a failed capture from an idle recorder and surface
     * an error instead of waiting forever for a [Finished] that never comes.
     */
    data object Failed : RecordingState
}

/**
 * Whether this is a terminal recording state — capture is no longer in progress
 * because it [Finished] or [Failed]. An observer collecting [AudioRecorder.state]
 * should stop on any terminal state, not only [RecordingState.Finished].
 */
val RecordingState.isTerminal: Boolean
    get() = this is RecordingState.Finished || this is RecordingState.Failed
