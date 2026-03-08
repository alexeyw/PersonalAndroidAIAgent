package ai.agent.android.data.engine

import android.content.Context
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.Result
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of [LlmInferenceEngine] using the MediaPipe GenAI library.
 * 
 * This class wraps the MediaPipe [LlmInference] class, providing suspension
 * functions and Flows instead of raw callbacks to fit cleanly into Coroutines.
 * 
 * @property context The application context required for initializing MediaPipe tasks.
 */
@Singleton
class MediaPipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmInferenceEngine {

    private var llmInference: LlmInference? = null
    
    // A shared flow to emit tokens to the currently active stream
    private var currentTokenFlow: MutableSharedFlow<TokenResult>? = null

    /**
     * Internal error mapping implementation for system/unknown errors.
     */
    private object LlmSystemError : AppError.System
    
    // We use a private sealed class for token results to handle completion
    private sealed class TokenResult {
        data class Token(val text: String) : TokenResult()
        object Done : TokenResult()
        data class Error(val exception: Exception) : TokenResult()
    }

    /**
     * Initializes the LLM Inference task by configuring its options with the
     * specified model path on disk.
     * 
     * @param modelPath The exact path to the locally downloaded model.
     * @return [Result.Success] on successful initialization, or [Result.Error] on failure.
     */
    override suspend fun initialize(modelPath: String): Result<Unit, AppError> {
        return try {
            val file = File(modelPath)
            if (!file.exists()) {
                val errorMsg = "Model file does not exist at path: $modelPath"
                Timber.e(errorMsg)
                return Result.Error(
                    error = LlmSystemError,
                    message = errorMsg
                )
            }

            // Close existing engine if present
            close()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setResultListener { partialResult, done ->
                    val flow = currentTokenFlow
                    if (flow != null) {
                        flow.tryEmit(if (done) TokenResult.Done else TokenResult.Token(partialResult))
                    }
                }
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MediaPipeLlmEngine")
            Result.Error(
                error = LlmSystemError,
                message = e.localizedMessage ?: "Unknown initialization error",
                throwable = e
            )
        }
    }

    /**
     * Triggers generation of the response asynchronously and emits each token via a Flow.
     * 
     * @param prompt The string prompt.
     * @return A [Flow] of strings containing the generated partial responses.
     */
    override fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val engine = llmInference
        if (engine == null) {
            val error = IllegalStateException("LLM Engine not initialized")
            Timber.e(error)
            close(error)
            return@callbackFlow
        }

        // Initialize a new shared flow for this specific stream with extra capacity
        val tokenFlow = MutableSharedFlow<TokenResult>(extraBufferCapacity = 64)
        currentTokenFlow = tokenFlow

        try {
            engine.generateResponseAsync(prompt)
        } catch (e: Exception) {
            Timber.e(e, "Error starting generation")
            close(e)
            return@callbackFlow
        }

        // Collect tokens from the shared flow
        val job = kotlinx.coroutines.launch {
            tokenFlow.collect { result ->
                when (result) {
                    is TokenResult.Token -> {
                        trySend(result.text)
                    }
                    is TokenResult.Done -> {
                        close() // Close the callback flow successfully
                    }
                    is TokenResult.Error -> {
                        close(result.exception) // Close with error
                    }
                }
            }
        }

        awaitClose {
            job.cancel()
            currentTokenFlow = null
        }
    }

    /**
     * Closes the engine and releases any underlying resources.
     */
    override fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing LLM inference engine")
        } finally {
            llmInference = null
            currentTokenFlow = null
        }
    }
}