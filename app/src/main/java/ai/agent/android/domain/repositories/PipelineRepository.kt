package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.PipelineGraph
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Visual Orchestrator pipelines.
 */
interface PipelineRepository {
    /**
     * Retrieves a flow of all saved pipelines.
     *
     * @return A [Flow] emitting the list of [PipelineGraph]s.
     */
    fun getAllPipelines(): Flow<List<PipelineGraph>>

    /**
     * Retrieves a specific pipeline by its ID.
     *
     * @param pipelineId The unique identifier of the pipeline.
     * @return The [PipelineGraph] if found, null otherwise.
     */
    suspend fun getPipelineById(pipelineId: String): PipelineGraph?

    /**
     * Saves a pipeline graph. If it already exists, it updates it.
     *
     * @param pipeline The [PipelineGraph] to save.
     */
    suspend fun savePipeline(pipeline: PipelineGraph)

    /**
     * Deletes a pipeline by its ID.
     *
     * @param pipelineId The unique identifier of the pipeline to delete.
     */
    suspend fun deletePipeline(pipelineId: String)
}
