package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ai.agent.android.data.local.models.ConnectionEntity
import ai.agent.android.data.local.models.NodeEntity
import ai.agent.android.data.local.models.PipelineEntity
import ai.agent.android.data.local.models.PipelineWithNodesAndConnections
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for visual orchestrator pipelines.
 */
@Dao
interface PipelineDao {

    /**
     * Retrieves all saved pipelines with their nodes and connections.
     */
    @Transaction
    @Query("SELECT * FROM pipelines")
    fun getAllPipelines(): Flow<List<PipelineWithNodesAndConnections>>

    /**
     * Retrieves a specific pipeline by ID.
     */
    @Transaction
    @Query("SELECT * FROM pipelines WHERE id = :pipelineId")
    suspend fun getPipelineById(pipelineId: String): PipelineWithNodesAndConnections?

    /**
     * Inserts or updates a pipeline entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(pipeline: PipelineEntity)

    /**
     * Inserts or updates a list of nodes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Inserts or updates a list of connections.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<ConnectionEntity>)

    /**
     * Deletes nodes associated with a given pipeline ID.
     */
    @Query("DELETE FROM pipeline_nodes WHERE pipelineId = :pipelineId")
    suspend fun deleteNodesForPipeline(pipelineId: String)

    /**
     * Deletes connections associated with a given pipeline ID.
     */
    @Query("DELETE FROM pipeline_connections WHERE pipelineId = :pipelineId")
    suspend fun deleteConnectionsForPipeline(pipelineId: String)

    /**
     * Completely replaces a pipeline's nodes and connections.
     */
    @Transaction
    suspend fun savePipelineTransaction(
        pipeline: PipelineEntity,
        nodes: List<NodeEntity>,
        connections: List<ConnectionEntity>
    ) {
        insertPipeline(pipeline)
        deleteNodesForPipeline(pipeline.id)
        deleteConnectionsForPipeline(pipeline.id)
        insertNodes(nodes)
        insertConnections(connections)
    }

    /**
     * Deletes a pipeline by ID. Associated nodes and connections are deleted via cascade.
     */
    @Query("DELETE FROM pipelines WHERE id = :pipelineId")
    suspend fun deletePipelineById(pipelineId: String)
}
