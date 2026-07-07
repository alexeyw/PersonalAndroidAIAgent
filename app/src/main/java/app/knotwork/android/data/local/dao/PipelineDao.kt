package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.knotwork.android.data.local.models.ConnectionEntity
import app.knotwork.android.data.local.models.NodeEntity
import app.knotwork.android.data.local.models.PipelineEntity
import app.knotwork.android.data.local.models.PipelineWithNodesAndConnections
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
     * Lightweight projection of every pipeline's id + display name, with no
     * nodes/connections join. Backs name-resolution callers (e.g. the usage
     * statistics screen) that need only `id → name` and would otherwise pay the
     * full graph load of [getAllPipelines].
     *
     * @return A [Flow] emitting the id/name rows of all saved pipelines.
     */
    @Query("SELECT id, name FROM pipelines ORDER BY updatedAt DESC")
    fun observePipelineNames(): Flow<List<PipelineNameRow>>

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
     * Atomically replaces **several** pipelines' meta, nodes and connections in
     * a single transaction — either all pipelines land or none do. Backs the
     * bundle-import path, where a single structurally-broken graph must roll
     * back the whole import rather than leave a half-written closure behind.
     *
     * Each pipeline is fully replaced (meta upsert + old nodes/connections
     * purged) before the fresh nodes and connections for the whole set are
     * inserted, mirroring the single-pipeline [savePipelineTransaction]
     * semantics across the batch. Callers must pass every node / connection
     * tagged with its owning `pipelineId`.
     *
     * @param pipelines The pipeline meta rows to upsert.
     * @param nodes All nodes across every pipeline in [pipelines].
     * @param connections All connections across every pipeline in [pipelines].
     */
    @Transaction
    suspend fun savePipelinesTransaction(
        pipelines: List<PipelineEntity>,
        nodes: List<NodeEntity>,
        connections: List<ConnectionEntity>,
    ) {
        pipelines.forEach { pipeline ->
            insertPipeline(pipeline)
            deleteNodesForPipeline(pipeline.id)
            deleteConnectionsForPipeline(pipeline.id)
        }
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

/**
 * Lightweight `id → name` projection of a pipeline row (no nodes/connections).
 *
 * @property id The pipeline id.
 * @property name The pipeline display name.
 */
data class PipelineNameRow(val id: String, val name: String)
