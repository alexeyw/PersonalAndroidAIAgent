package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a connection between two nodes in a pipeline.
 *
 * @property id The unique identifier of the connection.
 * @property pipelineId The ID of the pipeline this connection belongs to.
 * @property sourceNodeId The ID of the starting node.
 * @property targetNodeId The ID of the ending node.
 * @property label Optional label for the connection.
 */
@Entity(
    tableName = "pipeline_connections",
    foreignKeys = [
        ForeignKey(
            entity = PipelineEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipelineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pipelineId")]
)
data class ConnectionEntity(
    @PrimaryKey
    val id: String,
    val pipelineId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val label: String? = null
)
