package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.PipelineGraph
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
     * Retrieves a flow of every pipeline's `id → display name` only, without
     * loading the (potentially large) node/connection graphs. For callers that
     * just need to label pipeline ids (e.g. the usage-statistics screen).
     *
     * @return A [Flow] emitting the `pipelineId → name` map of all pipelines.
     */
    fun observePipelineNames(): Flow<Map<String, String>>

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
     * Atomically saves several pipeline graphs in a single transaction —
     * either all of them are persisted or none are. Backs the bundle-import
     * flow, where one structurally-broken graph must roll back the entire
     * import rather than leave a partially-written dependency closure.
     *
     * @param pipelines The pipeline graphs to persist as one atomic unit.
     */
    suspend fun savePipelines(pipelines: List<PipelineGraph>)

    /**
     * Deletes a pipeline by its ID.
     *
     * @param pipelineId The unique identifier of the pipeline to delete.
     */
    suspend fun deletePipeline(pipelineId: String)
}
