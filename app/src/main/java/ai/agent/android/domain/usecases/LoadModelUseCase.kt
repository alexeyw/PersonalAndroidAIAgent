package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * UseCase for loading the active model into the inference engine.
 */
class LoadModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val llmInferenceEngine: LlmInferenceEngine
) {

    /**
     * Internal error mapping implementation for system/unknown errors.
     */
    private object LlmSystemError : AppError.System

    /**
     * Retrieves the currently active model from the repository, checks if the file exists on disk,
     * and initializes the LLM engine with the model path.
     * 
     * If the active model is already loaded in the engine, it returns [Result.Success] immediately.
     * 
     * @return [Result.Success] if the active model was successfully loaded, or [Result.Error] otherwise.
     */
    suspend operator fun invoke(): Result<Unit, AppError> = withContext(Dispatchers.IO) {
        try {
            val activeModel = localModelRepository.getActiveModel()
                ?: return@withContext Result.Error(
                    error = LlmSystemError,
                    message = "No active model found in the database. Please select a model in settings."
                )

            // Check if the engine is already initialized with this exact model
            if (llmInferenceEngine.isInitialized && llmInferenceEngine.currentModelPath == activeModel.path) {
                return@withContext Result.Success(Unit)
            }

            val file = File(activeModel.path)
            if (!file.exists()) {
                return@withContext Result.Error(
                    error = LlmSystemError,
                    message = "Active model file not found at: ${activeModel.path}. Please download it again."
                )
            }

            return@withContext llmInferenceEngine.initialize(activeModel.path)
        } catch (e: Exception) {
            return@withContext Result.Error(
                error = LlmSystemError,
                message = e.localizedMessage ?: "Unknown error while loading model",
                throwable = e
            )
        }
    }
}
