package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import javax.inject.Inject

/**
 * Use case for saving a visual orchestrator pipeline.
 *
 * @property pipelineRepository The repository used for storing pipelines.
 */
class SavePipelineUseCase @Inject constructor(
    private val pipelineRepository: PipelineRepository
) {
    /**
     * Executes the use case to save a pipeline.
     *
     * @param pipeline The [PipelineGraph] to save.
     * @return A [Result] indicating success or containing an exception if validation fails.
     */
    suspend operator fun invoke(pipeline: PipelineGraph): Result<Unit> {
        if (!pipeline.isValidDAG()) {
            return Result.failure(IllegalStateException("The pipeline graph contains cycles and is not a valid DAG."))
        }
        
        return try {
            pipelineRepository.savePipeline(pipeline)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
