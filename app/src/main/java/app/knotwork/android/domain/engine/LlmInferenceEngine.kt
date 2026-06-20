package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.Result
import kotlinx.coroutines.flow.Flow

/**
 * Interface representing the LLM Inference Engine.
 *
 * This engine is responsible for loading a local LLM from the device's storage,
 * generating text based on a prompt, and providing responses either as a complete
 * string or as a stream of tokens.
 */
interface LlmInferenceEngine {

    /**
     * Initializes the LLM engine with the given model path.
     *
     * @param modelPath The absolute path to the model file on the device.
     * @param enableVision When `true`, the engine is configured with a vision
     *   backend so the model can accept an image alongside the text prompt (see
     *   [generateResponseStream]). Defaults to `false`: a text-only run never
     *   pays the extra vision-encoder memory cost, and only a run carrying an
     *   image (gated by the pre-flight vision check) requests `true`. Toggling
     *   this relative to the currently loaded mode forces a re-initialization —
     *   the underlying runtime fixes the vision backend at engine-construction
     *   time.
     * @param enableAudio When `true`, the engine is configured with an audio
     *   backend so the model can transcribe an audio clip (see [transcribe]).
     *   Defaults to `false` for the same reason as [enableVision]: only the
     *   voice-input transcription step (gated by the active model's audio-support
     *   flag) requests `true`, and toggling it relative to the loaded mode forces
     *   a re-initialization, since the runtime fixes the audio backend at
     *   engine-construction time.
     * @return A [Result] indicating success or containing an [AppError] if initialization failed.
     */
    suspend fun initialize(
        modelPath: String,
        enableVision: Boolean = false,
        enableAudio: Boolean = false,
    ): Result<Unit, AppError>

    /**
     * Returns true if the LLM engine is currently initialized with a model.
     */
    val isInitialized: Boolean

    /**
     * Returns the absolute path of the currently loaded model, or null if none is loaded.
     */
    val currentModelPath: String?

    /**
     * Whether the currently loaded engine was initialized with its vision
     * backend enabled (`enableVision = true` on the last [initialize]). `false`
     * when no model is loaded or the model was loaded text-only. Used by
     * `LoadModelUseCase` to decide whether an image-carrying run needs a
     * vision-enabling re-initialization of an already-loaded model.
     */
    val isVisionEnabled: Boolean

    /**
     * Whether the currently loaded engine was initialized with its audio
     * backend enabled (`enableAudio = true` on the last [initialize]). `false`
     * when no model is loaded or the model was loaded without audio. Used by
     * `LoadModelUseCase` to decide whether a transcription request needs an
     * audio-enabling re-initialization of an already-loaded model.
     */
    val isAudioEnabled: Boolean

    /**
     * Transcribes a single audio clip into text using the loaded multimodal
     * model. This is a **preprocessing** step that runs *before* any pipeline:
     * voice input never travels the execution graph — only the resulting text
     * does, as an ordinary editable message. It therefore has its own entry
     * point rather than overloading [generateResponseStream] (whose
     * image/temperature contract belongs to graph inference).
     *
     * @param audioPath Absolute filesystem path of the audio clip to transcribe
     *   (a 16 kHz mono PCM WAV produced by the recorder, or a copy of a picked
     *   audio file). Requires the engine to have been initialized with
     *   [initialize]'s `enableAudio = true`, which the caller guarantees by
     *   loading the model in audio mode before issuing a transcription.
     * @param prompt The transcription instruction sent alongside the audio (the
     *   rendered transcription system prompt).
     * @return A [Flow] of strings streaming the transcript tokens as they are
     *   produced; the caller joins them into the final transcript text.
     */
    fun transcribe(audioPath: String, prompt: String): Flow<String>

    /**
     * Generates a response stream from the LLM based on the provided prompt.
     *
     * @param prompt The input text prompt for the LLM.
     * @param imagePath Absolute filesystem path of a single image to send
     *   alongside [prompt]. When `null` (every text-only call) generation is
     *   purely textual. When non-`null` the engine prepends the image to the
     *   prompt content; this requires the engine to have been initialized with
     *   [initialize]'s `enableVision = true`, which the caller guarantees by
     *   loading the model in vision mode before issuing an image generation.
     * @param temperature Optional sampling-temperature override for this single
     *   generation. When `null` (the default for every ordinary call) the engine
     *   leaves the model's built-in sampler untouched, so normal generation is
     *   unaffected. When non-`null` the engine drives a deterministic-leaning
     *   sampler at the requested temperature — used by the structured-output
     *   repair loop (see
     *   [app.knotwork.android.domain.engine.structured.StructuredOutputGate.REPAIR_TEMPERATURE])
     *   to nudge a stumbling model towards a schema-obedient retry.
     * @return A [Flow] of strings representing the generated tokens as they are produced.
     */
    fun generateResponseStream(prompt: String, imagePath: String? = null, temperature: Float? = null): Flow<String>

    /**
     * Closes the engine and releases any underlying resources.
     *
     * Should be called when the engine is no longer needed to prevent memory leaks.
     */
    fun close()

    /**
     * Unloads the engine from memory, releasing heavy resources without fully destroying the manager.
     *
     * Used for temporary memory relief (e.g., when the app goes into the background or onTrimMemory).
     */
    fun unload()
}
