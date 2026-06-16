package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineValidationException
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.services.PipelineCompositionValidator
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Use case for saving a visual orchestrator pipeline.
 *
 * @property pipelineRepository The repository used for storing pipelines.
 * @property compositionValidator Cross-pipeline validator that rejects
 * PIPELINE-node compositions with cycles, dangling references, or nesting
 * deeper than the configured ceiling before they can be persisted.
 */
class SavePipelineUseCase @Inject constructor(
    private val pipelineRepository: PipelineRepository,
    private val compositionValidator: PipelineCompositionValidator,
) {
    /**
     * Executes the use case to save a pipeline.
     *
     * @param pipeline The [PipelineGraph] to save.
     * @return A [Result] indicating success or containing an exception if validation fails.
     */
    suspend operator fun invoke(pipeline: PipelineGraph): Result<Unit> {
        // Structural single-graph checks plus cross-pipeline composition checks.
        // Both run so the validation bar can surface every problem at once.
        val errors = pipeline.validate() + compositionValidator.validate(pipeline)
        if (errors.isNotEmpty()) {
            return Result.failure(PipelineValidationException(errors))
        }

        return try {
            pipelineRepository.savePipeline(pipeline)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
