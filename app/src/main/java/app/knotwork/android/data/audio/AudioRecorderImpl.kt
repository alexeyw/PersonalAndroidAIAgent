package app.knotwork.android.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.knotwork.android.domain.services.AudioCaptureStore
import app.knotwork.android.domain.services.AudioRecorder
import app.knotwork.android.domain.services.RecordingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * [AudioRecorder] backed by the platform [AudioRecord]. Captures 16 kHz mono
 * 16-bit PCM from the mic into a WAV ([WavHeader]) inside the ephemeral
 * [AudioCaptureStore], publishing a ticking elapsed time and auto-stopping at the
 * caller's limit.
 *
 * The mic is opened only after the chat composer's runtime-permission gate, so
 * the [AudioRecord] construction is annotated [SuppressLint] for `MissingPermission`.
 *
 * @property audioCaptureStore allocates the clip path and cleans up on cancel.
 */
@Singleton
class AudioRecorderImpl @Inject constructor(private val audioCaptureStore: AudioCaptureStore) : AudioRecorder {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Scope the capture loop runs in; overridable in tests. */
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stopRequested = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private var captureJob: Job? = null
    private var currentPath: String? = null

    override suspend fun start(maxDurationSec: Int) {
        if (_state.value is RecordingState.Recording) return
        val path = audioCaptureStore.newRecordingFile()
        currentPath = path
        stopRequested.set(false)
        cancelled.set(false)
        _state.value = RecordingState.Recording(elapsedSec = 0, maxSec = maxDurationSec)
        captureJob = scope.launch { captureLoop(path, maxDurationSec) }
    }

    override suspend fun stop(): String? {
        stopRequested.set(true)
        captureJob?.join()
        return (_state.value as? RecordingState.Finished)?.path
    }

    override fun cancel() {
        cancelled.set(true)
        stopRequested.set(true)
        captureJob?.cancel()
        currentPath?.let { path -> scope.launch { audioCaptureStore.delete(path) } }
        currentPath = null
        _state.value = RecordingState.Idle
    }

    @SuppressLint("MissingPermission") // Mic is opened only after the composer permission gate.
    private suspend fun captureLoop(path: String, maxDurationSec: Int) {
        val minBuffer = AudioRecord.getMinBufferSize(
            WavHeader.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = if (minBuffer > 0) minBuffer else FALLBACK_BUFFER_BYTES
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavHeader.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to construct AudioRecord")
            failCapture(path)
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord failed to initialize")
            record.release()
            failCapture(path)
            return
        }

        try {
            captureToWav(record, path, maxDurationSec, bufferSize)
            if (!cancelled.get()) {
                _state.value = RecordingState.Finished(path)
            }
        } catch (e: CancellationException) {
            // A cancel() aborts the capture; cleanup already happened there.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Audio capture failed")
            failCapture(path)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    @SuppressLint("MissingPermission") // Mic is opened only after the composer permission gate.
    private suspend fun captureToWav(record: AudioRecord, path: String, maxDurationSec: Int, bufferSize: Int) {
        record.startRecording()
        val maxBytes = WavHeader.SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * maxDurationSec
        var totalBytes = 0
        var lastSecond = 0
        RandomAccessFile(path, "rw").use { file ->
            // Reserve the header; patched with real sizes once capture ends.
            file.write(ByteArray(WavHeader.HEADER_BYTES))
            val buffer = ByteArray(bufferSize)
            while (!stopRequested.get() && totalBytes < maxBytes && coroutineContext.isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    file.write(buffer, 0, read)
                    totalBytes += read
                    val second = totalBytes / (WavHeader.SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)
                    if (second != lastSecond) {
                        lastSecond = second
                        _state.value = RecordingState.Recording(elapsedSec = second, maxSec = maxDurationSec)
                    }
                } else if (read < 0) {
                    break
                }
            }
            file.seek(0)
            file.write(WavHeader.build(totalBytes))
        }
    }

    private suspend fun failCapture(path: String) {
        audioCaptureStore.delete(path)
        currentPath = null
        _state.value = RecordingState.Idle
    }

    private companion object {
        /** Bytes per 16-bit mono PCM sample. */
        const val BYTES_PER_SAMPLE = 2

        /** Fallback capture buffer when the platform min-size query fails. */
        const val FALLBACK_BUFFER_BYTES = 4096
    }
}
