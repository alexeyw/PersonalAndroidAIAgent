package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.CancellationException
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
     * If the requested model is already loaded in the engine — and already in the
     * vision mode the caller needs — it returns [Result.Success] immediately. An
     * image-carrying run ([requireVision] = `true`) against an engine currently
     * loaded text-only forces a vision-enabling re-initialization, since the
     * runtime fixes its vision backend at construction time.
     *
     * @param modelPath Optional absolute path to the model file. If null, the globally active model is used.
     * @param requireVision When `true`, the engine must be (re-)initialized with
     *   its vision backend so the run can attach an image. Defaults to `false`
     *   for every text-only run. The pre-flight send guard guarantees the active
     *   model is vision-capable before a `true` load is requested.
     * @return [Result.Success] if the model was successfully loaded, or [Result.Error] otherwise.
     */
    suspend operator fun invoke(modelPath: String? = null, requireVision: Boolean = false): Result<Unit, AppError> =
        withContext(Dispatchers.IO) {
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

                // Reuse the loaded engine only when it already covers the requested
                // vision mode — an image run needs vision on, but a text run is happy
                // to reuse an already vision-enabled engine (no point downgrading).
                val visionModeSatisfied = llmInferenceEngine.isVisionEnabled || !requireVision
                if (
                    llmInferenceEngine.isInitialized &&
                    llmInferenceEngine.currentModelPath == pathToLoad &&
                    visionModeSatisfied
                ) {
                    return@withContext Result.Success(Unit)
                }

                val file = File(pathToLoad)
                if (!file.exists()) {
                    return@withContext Result.Error(
                        error = LlmSystemError,
                        message = "Model file not found at: $pathToLoad. Please download it again.",
                    )
                }

                // Keep vision on if it was already enabled for THIS model (a prior
                // image run). Re-initialization is a full model reload (unload +
                // reconstruct the engine), so downgrading just to drop the vision
                // encoder would cost far more than the memory it reclaims — the
                // encoder is released instead when the engine is unloaded on
                // background / memory-trim. A model switch resets to the requested
                // mode, so vision is never forced onto a freshly loaded text-only model.
                val enableVision = requireVision ||
                    (llmInferenceEngine.isVisionEnabled && llmInferenceEngine.currentModelPath == pathToLoad)
                return@withContext llmInferenceEngine.initialize(pathToLoad, enableVision = enableVision)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext Result.Error(
                    error = LlmSystemError,
                    message = e.localizedMessage ?: "Unknown error while loading model",
                    throwable = e,
                )
            }
        }
}
