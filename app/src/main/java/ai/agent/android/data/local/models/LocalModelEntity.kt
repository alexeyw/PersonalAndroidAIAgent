package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a downloaded LLM model's metadata.
 * 
 * @property id Unique identifier for the model record.
 * @property name Human-readable name of the model.
 * @property path Absolute path to the model file on the device.
 * @property size Size of the model file in bytes.
 * @property isActive Flag indicating if this model is currently active/selected.
 */
@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isActive: Boolean
)
