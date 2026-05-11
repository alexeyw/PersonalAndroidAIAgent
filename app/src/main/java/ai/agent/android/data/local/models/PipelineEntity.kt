package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved visual orchestrator pipeline.
 *
 * @property id The unique identifier of the pipeline.
 * @property name The display name of the pipeline.
 */
@Entity(tableName = "pipelines")
data class PipelineEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
