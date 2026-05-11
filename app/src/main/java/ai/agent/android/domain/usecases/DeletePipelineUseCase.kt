package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.PipelineRepository
import javax.inject.Inject

/**
 * Use case for deleting a pipeline from the library.
 *
 * Encapsulates a single safety rule: a pipeline that is currently active
 * (loaded into the editor) cannot be deleted. The active id is supplied by
 * the caller because there is no global "active pipeline" persistence layer
 * yet — the orchestrator's in-memory state is the single source of truth.
 *
 * @property pipelineRepository Persistence sink for the cascading delete.
 */
class DeletePipelineUseCase @Inject constructor(private val pipelineRepository: PipelineRepository) {
    /**
     * Deletes the pipeline identified by [pipelineId].
     *
     * @param pipelineId Unique identifier of the pipeline to delete.
     * @param activePipelineId Identifier of the pipeline currently loaded in
     * the editor, or `null` if no pipeline is active. When this matches
     * [pipelineId] the call short-circuits with [IllegalStateException] and
     * no I/O is performed.
     * @return [Result.success] when the pipeline is gone, [Result.failure]
     * with [IllegalStateException] when blocked by the active-pipeline rule
     * or when the underlying delete throws.
     */
    suspend operator fun invoke(pipelineId: String, activePipelineId: String?): Result<Unit> {
        if (pipelineId == activePipelineId) {
            return Result.failure(
                IllegalStateException("Active pipeline cannot be deleted"),
            )
        }
        return try {
            pipelineRepository.deletePipeline(pipelineId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
