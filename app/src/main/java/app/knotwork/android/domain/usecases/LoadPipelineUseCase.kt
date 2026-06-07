package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for loading visual orchestrator pipelines.
 *
 * @property pipelineRepository The repository used for retrieving pipelines.
 */
class LoadPipelineUseCase @Inject constructor(private val pipelineRepository: PipelineRepository) {
    /**
     * Observes all saved pipelines.
     *
     * @return A [Flow] emitting the list of all stored [PipelineGraph]s.
     */
    fun observeAllPipelines(): Flow<List<PipelineGraph>> = pipelineRepository.getAllPipelines()

    /**
     * Loads a specific pipeline by its ID.
     *
     * @param pipelineId The ID of the pipeline to load.
     * @return The [PipelineGraph] if found, null otherwise.
     */
    suspend fun getPipelineById(pipelineId: String): PipelineGraph? = pipelineRepository.getPipelineById(pipelineId)
}
