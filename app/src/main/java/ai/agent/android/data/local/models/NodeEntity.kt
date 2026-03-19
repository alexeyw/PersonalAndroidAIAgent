package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single node in a pipeline.
 *
 * @property id The unique identifier of the node.
 * @property pipelineId The ID of the pipeline this node belongs to.
 * @property type The type of the node as a string.
 * @property x The X coordinate of the node on the canvas.
 * @property y The Y coordinate of the node on the canvas.
 * @property label The display label of the node.
 */
@Entity(
    tableName = "pipeline_nodes",
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
data class NodeEntity(
    @PrimaryKey
    val id: String,
    val pipelineId: String,
    val type: String,
    val x: Float,
    val y: Float,
    val label: String
)
