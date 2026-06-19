package app.knotwork.android.data.local.models

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
 * @property supportsVision Whether the user has marked this model as
 *   vision-capable (able to read an attached image). Added in schema v40
 *   (`MIGRATION_39_40`) with a `false` default so every pre-existing row stays
 *   text-only until the user opts in on the model screen.
 */
@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isActive: Boolean,
    val supportsVision: Boolean = false,
)
