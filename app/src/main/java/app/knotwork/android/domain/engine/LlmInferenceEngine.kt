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
     * @return A [Result] indicating success or containing an [AppError] if initialization failed.
     */
    suspend fun initialize(modelPath: String): Result<Unit, AppError>

    /**
     * Returns true if the LLM engine is currently initialized with a model.
     */
    val isInitialized: Boolean

    /**
     * Returns the absolute path of the currently loaded model, or null if none is loaded.
     */
    val currentModelPath: String?

    /**
     * Generates a response stream from the LLM based on the provided prompt.
     *
     * @param prompt The input text prompt for the LLM.
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
    fun generateResponseStream(prompt: String, temperature: Float? = null): Flow<String>

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
