package ai.agent.android.data.engine

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.Result
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of [LlmInferenceEngine] using the specialized LiteRT-LM library.
 * 
 * This engine manages the lifecycle of the LiteRT-LM [Engine], which is optimized
 * specifically for Large Language Models (LLMs) on edge devices.
 */
@Singleton
class LiteRTLlmEngine @Inject constructor() : LlmInferenceEngine {

    private var engine: Engine? = null

    /**
     * Internal error mapping implementation for system/unknown errors.
     */
    private object LlmSystemError : AppError.System

    /**
     * Initializes the LiteRT-LM engine by configuring its options with the
     * specified model path.
     * 
     * @param modelPath The exact path to the locally downloaded model file.
     * @return [Result.Success] on successful initialization, or [Result.Error] on failure.
     */
    override suspend fun initialize(modelPath: String): Result<Unit, AppError> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                val errorMsg = "Model file does not exist at path: $modelPath"
                Timber.e(errorMsg)
                return@withContext Result.Error(
                    error = LlmSystemError,
                    message = errorMsg
                )
            }

            // Close existing engine if present to release previous resources
            close()

            // Initialize Engine Configuration
            val config = EngineConfig(
                modelPath = modelPath
            )

            // Create Engine from config and initialize
            engine = Engine(config).apply {
                initialize()
            }
            Timber.i("LiteRT-LM Engine successfully initialized")
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiteRTLlmEngine")
            Result.Error(
                error = LlmSystemError,
                message = e.localizedMessage ?: "Unknown initialization error",
                throwable = e
            )
        }
    }

    /**
     * Generates a response stream from the LLM based on the provided prompt.
     * 
     * @param prompt The input text prompt for the LLM.
     * @return A [Flow] of strings representing the generated tokens as they are produced.
     */
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val currentEngine = engine
        if (currentEngine == null) {
            Timber.e("Engine is not initialized")
            throw IllegalStateException("LLM Engine not initialized")
        }

        try {
            Timber.d("Starting inference for prompt: %s", prompt)
            
            val conversation = currentEngine.createConversation()
            // Stream the tokens directly from the LiteRT-LM conversation
            conversation.sendMessageAsync(prompt).collect { chunk ->
                emit(chunk)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error during text generation")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Closes the engine and releases any underlying hardware resources.
     */
    override fun close() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing LiteRT-LM engine")
        } finally {
            engine = null
        }
    }
}