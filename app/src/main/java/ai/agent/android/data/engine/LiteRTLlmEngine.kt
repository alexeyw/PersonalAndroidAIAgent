package ai.agent.android.data.engine

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.SettingsRepository
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
class LiteRTLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : LlmInferenceEngine,
    ComponentCallbacks2 {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var _currentModelPath: String? = null

    /**
     * Indicates whether the engine has been successfully initialized and is ready for use.
     */
    override val isInitialized: Boolean get() = engine != null

    /**
     * Returns the file path of the currently loaded model, or null if no model is loaded.
     */
    override val currentModelPath: String? get() = _currentModelPath

    init {
        context.registerComponentCallbacks(this)
    }

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
                _currentModelPath = null
                return@withContext Result.Error(
                    error = LlmSystemError,
                    message = errorMsg,
                )
            }

            // Close existing engine if present to release previous resources
            unload()

            val maxTokens = settingsRepository.maxContextLength.first()
            val backendStr = settingsRepository.localModelBackend.first()

            val backend = when (backendStr) {
                "GPU" -> Backend.GPU()
                "NPU" -> Backend.NPU()
                else -> Backend.CPU()
            }

            // Initialize Engine Configuration
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = maxTokens,
                cacheDir = context.cacheDir.absolutePath,
            )

            // Create Engine from config and initialize
            engine = Engine(config).apply {
                initialize()
            }
            _currentModelPath = modelPath
            Timber.i("LiteRT-LM Engine successfully initialized with $modelPath")

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiteRTLlmEngine")
            _currentModelPath = null
            Result.Error(
                error = LlmSystemError,
                message = e.localizedMessage ?: "Unknown initialization error",
                throwable = e,
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

            // LiteRT-LM allows only one active session. Since the Orchestrator manually
            // supplies the full history context every time, we must close the old conversation
            // and create a fresh one to prevent token accumulation and OOM crashes.
            conversation?.close()
            conversation = currentEngine.createConversation()

            // Stream the tokens directly from the LiteRT-LM conversation
            conversation?.let { conversation ->
                conversation.sendMessageAsync(prompt).collect { chunk ->
                    val textParts = chunk.contents.contents.filterIsInstance<Content.Text>()
                    val text = textParts.joinToString("") { it.text }
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during text generation")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Unloads the engine from memory, releasing heavy resources.
     */
    override fun unload() {
        try {
            conversation?.close()
            engine?.close()
            Timber.i("LiteRT-LM engine unloaded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error unloading LiteRT-LM engine")
        } finally {
            conversation = null
            engine = null
            _currentModelPath = null
        }
    }

    /**
     * Closes the engine and removes the callbacks to prevent leaks.
     */
    override fun close() {
        unload()
        context.unregisterComponentCallbacks(this)
    }

    /**
     * Called by the system when the device configuration changes while your component is running.
     *
     * @param newConfig The new device configuration.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        // No action needed
    }

    /**
     * This is called when the overall system is running low on memory, and actively running processes should trim their memory usage.
     * Unloads the engine to free up resources.
     */
    override fun onLowMemory() {
        Timber.w("onLowMemory called, unloading engine")
        unload()
    }

    /**
     * Called when the operating system has determined that it is a good time for a process to trim unneeded memory from its process.
     * Unloads the engine if the memory trim level is critical.
     *
     * @param level The context of the trim, giving a hint of the amount of trimming the application may like to perform.
     */
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Timber.w("onTrimMemory called with critical level \$level, unloading engine")
            unload()
        }
    }
}
