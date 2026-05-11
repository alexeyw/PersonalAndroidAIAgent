package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.ConnectionEntity
import ai.agent.android.data.local.models.NodeEntity
import ai.agent.android.data.local.models.PipelineEntity
import ai.agent.android.data.local.models.PipelineWithNodesAndConnections
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for visual orchestrator pipelines.
 */
@Dao
interface PipelineDao {

    /**
     * Retrieves all saved pipelines with their nodes and connections.
     *
     * @return A [Flow] emitting a list of [PipelineWithNodesAndConnections].
     */
    @Transaction
    @Query("SELECT * FROM pipelines ORDER BY updatedAt DESC")
    fun getAllPipelines(): Flow<List<PipelineWithNodesAndConnections>>

    /**
     * Retrieves a specific pipeline by ID.
     *
     * @param pipelineId The ID of the pipeline to retrieve.
     * @return The [PipelineWithNodesAndConnections] or null if not found.
     */
    @Transaction
    @Query("SELECT * FROM pipelines WHERE id = :pipelineId")
    suspend fun getPipelineById(pipelineId: String): PipelineWithNodesAndConnections?

    /**
     * Inserts or updates a pipeline entity.
     *
     * @param pipeline The [PipelineEntity] to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(pipeline: PipelineEntity)

    /**
     * Inserts or updates a list of nodes.
     *
     * @param nodes The list of [NodeEntity] to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Inserts or updates a list of connections.
     *
     * @param connections The list of [ConnectionEntity] to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<ConnectionEntity>)

    /**
     * Deletes nodes associated with a given pipeline ID.
     *
     * @param pipelineId The ID of the pipeline whose nodes should be deleted.
     */
    @Query("DELETE FROM pipeline_nodes WHERE pipelineId = :pipelineId")
    suspend fun deleteNodesForPipeline(pipelineId: String)

    /**
     * Deletes connections associated with a given pipeline ID.
     *
     * @param pipelineId The ID of the pipeline whose connections should be deleted.
     */
    @Query("DELETE FROM pipeline_connections WHERE pipelineId = :pipelineId")
    suspend fun deleteConnectionsForPipeline(pipelineId: String)

    /**
     * Completely replaces a pipeline's nodes and connections.
     *
     * @param pipeline The new [PipelineEntity].
     * @param nodes The new list of [NodeEntity].
     * @param connections The new list of [ConnectionEntity].
     */
    @Transaction
    suspend fun savePipelineTransaction(
        pipeline: PipelineEntity,
        nodes: List<NodeEntity>,
        connections: List<ConnectionEntity>,
    ) {
        insertPipeline(pipeline)
        deleteNodesForPipeline(pipeline.id)
        deleteConnectionsForPipeline(pipeline.id)
        insertNodes(nodes)
        insertConnections(connections)
    }

    /**
     * Deletes a pipeline by ID. Associated nodes and connections are deleted via cascade.
     *
     * @param pipelineId The ID of the pipeline to delete.
     */
    @Query("DELETE FROM pipelines WHERE id = :pipelineId")
    suspend fun deletePipelineById(pipelineId: String)
}
