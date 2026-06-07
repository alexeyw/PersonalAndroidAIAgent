package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * UseCase for loading a model into the inference engine.
 * Supports loading a specific model by path, or the default active model.
 */
class LoadModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val llmInferenceEngine: LlmInferenceEngine,
) {

    /**
     * Internal error mapping implementation for system/unknown errors.
     */
    private object LlmSystemError : AppError.System

    /**
     * Retrieves the specified model or the currently active model from the repository, checks if the file exists on disk,
     * and initializes the LLM engine with the model path.
     *
     * If the requested model is already loaded in the engine, it returns [Result.Success] immediately.
     *
     * @param modelPath Optional absolute path to the model file. If null, the globally active model is used.
     * @return [Result.Success] if the model was successfully loaded, or [Result.Error] otherwise.
     */
    suspend operator fun invoke(modelPath: String? = null): Result<Unit, AppError> = withContext(Dispatchers.IO) {
        try {
            // Blank `modelPath` is the "Active model" sentinel persisted by
            // the LITE_RT node form — fall back to `getActiveModel()` just
            // like the null case. Centralising the coercion here keeps every
            // executor's call site uniform.
            val requestedPath = modelPath?.takeIf { it.isNotBlank() }
            val pathToLoad = requestedPath ?: localModelRepository.getActiveModel()?.path
                ?: return@withContext Result.Error(
                    error = LlmSystemError,
                    message = "No active model found. Please select a model in settings or provide a path.",
                )

            // Check if the engine is already initialized with this exact model
            if (llmInferenceEngine.isInitialized && llmInferenceEngine.currentModelPath == pathToLoad) {
                return@withContext Result.Success(Unit)
            }

            val file = File(pathToLoad)
            if (!file.exists()) {
                return@withContext Result.Error(
                    error = LlmSystemError,
                    message = "Model file not found at: $pathToLoad. Please download it again.",
                )
            }

            return@withContext llmInferenceEngine.initialize(pathToLoad)
        } catch (e: Exception) {
            return@withContext Result.Error(
                error = LlmSystemError,
                message = e.localizedMessage ?: "Unknown error while loading model",
                throwable = e,
            )
        }
    }
}
