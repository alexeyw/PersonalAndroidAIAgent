package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.services.AudioCaptureStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import javax.inject.Inject

/**
 * Transcribes a recorded or picked audio clip into text, **before** any
 * pipeline runs. This is the deliberate simplification of voice input: the audio
 * never travels the execution graph — the active multimodal model turns it into
 * text here, and that text becomes an ordinary editable message the user reviews
 * and sends. The whole pipeline therefore stays text-only.
 *
 * The clip is ephemeral: this use case deletes it through [audioCaptureStore]
 * once it has been consumed (on success **and** on terminal failure), keeping
 * the audio cache clean. The one exception is [TranscriptionOutcome.EngineBusy]:
 * the clip is preserved so the caller can retry the same path once the engine
 * frees up, rather than forcing the user to re-record.
 *
 * @property localModelRepository Source of the active model and its
 *   [app.knotwork.android.domain.models.LocalModel.supportsAudio] flag.
 * @property loadModelUseCase Loads the active model in audio mode before
 *   transcribing (`requireAudio = true`).
 * @property llmInferenceEngine The engine whose [LlmInferenceEngine.transcribe]
 *   produces the transcript stream.
 * @property audioCaptureStore Owns the ephemeral clip; used here only to delete
 *   the consumed file.
 * @property taskQueueManager Source of the agent-busy signal: transcription must
 *   not seize the single inference conversation mid-run, so it is refused while a
 *   foreground pipeline is active (the same gate the background coordinators use).
 */
class TranscribeAudioUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val audioCaptureStore: AudioCaptureStore,
    private val taskQueueManager: TaskQueueManager,
) {

    /**
     * Transcribes the clip at [audioPath].
     *
     * @param audioPath Absolute path of the clip to transcribe (recorder output
     *   or a copy of a picked file). Deleted before returning, except on
     *   [TranscriptionOutcome.EngineBusy].
     * @return The [TranscriptionOutcome] describing success or the reason
     *   transcription could not run.
     */
    suspend operator fun invoke(audioPath: String): TranscriptionOutcome {
        // Refuse while a pipeline is generating: the engine allows one active
        // conversation, so transcribing now would tear down the in-flight run.
        // Keep the clip so an immediate retry (once idle) reuses it.
        if (isAgentBusy()) {
            return TranscriptionOutcome.EngineBusy
        }

        val activeModel = localModelRepository.getActiveModel()
        if (activeModel == null) {
            audioCaptureStore.delete(audioPath)
            return TranscriptionOutcome.NoActiveModel
        }
        if (!activeModel.supportsAudio) {
            audioCaptureStore.delete(audioPath)
            return TranscriptionOutcome.ModelNotAudioCapable
        }

        return try {
            when (val load = loadModelUseCase(requireAudio = true)) {
                is Result.Error -> TranscriptionOutcome.Failed(
                    load.message ?: "Failed to load the model for transcription",
                )
                is Result.Success -> {
                    val transcript = llmInferenceEngine
                        .transcribe(audioPath, DefaultPrompts.AUDIO_TRANSCRIPTION_INSTRUCTION)
                        .toList()
                        .joinToString(separator = "")
                        .trim()
                    TranscriptionOutcome.Success(transcript)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Audio transcription failed")
            TranscriptionOutcome.Failed(e.localizedMessage ?: "Transcription failed")
        } finally {
            // Ephemeral clip: drop it once consumed, regardless of outcome.
            audioCaptureStore.delete(audioPath)
        }
    }

    /**
     * `true` when the agent is mid-run on the shared inference engine — i.e.
     * [TaskQueueManager.globalState] is any non-terminal state. Mirrors the idle
     * predicate the background coordinators use (idle = `Idle` / `Completed` /
     * `Error`).
     */
    private fun isAgentBusy(): Boolean = when (taskQueueManager.globalState.value) {
        is AgentOrchestratorState.Idle,
        is AgentOrchestratorState.Completed,
        is AgentOrchestratorState.Error,
        -> false

        else -> true
    }
}

/**
 * Outcome of a [TranscribeAudioUseCase] invocation.
 */
sealed interface TranscriptionOutcome {
    /**
     * Transcription succeeded.
     *
     * @property transcript The trimmed transcript text (may be empty when the
     *   clip held no recognizable speech).
     */
    data class Success(val transcript: String) : TranscriptionOutcome

    /** No model is active, so nothing can transcribe. */
    data object NoActiveModel : TranscriptionOutcome

    /** The active model is not marked audio-capable (`supportsAudio = false`). */
    data object ModelNotAudioCapable : TranscriptionOutcome

    /** A pipeline run is active; transcription is refused to avoid seizing the engine. */
    data object EngineBusy : TranscriptionOutcome

    /**
     * Transcription failed (model load or inference error).
     *
     * @property message Human-readable failure reason for the UI.
     */
    data class Failed(val message: String) : TranscriptionOutcome
}
