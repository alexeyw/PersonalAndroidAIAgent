package app.knotwork.android.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a user-defined automation trigger (`triggers` table, v45).
 *
 * The activation condition is stored as a compact JSON string in
 * [conditionJson] (produced by
 * [app.knotwork.android.domain.triggerio.TriggerConditionCodec]) rather than
 * spread across type-specific columns, so the schema stays narrow and a new
 * condition shape needs no migration. [enabled] is indexed because the
 * active-trigger query (the scheduler sync's hot path) filters on it.
 *
 * @property id Stable unique id (UUID), primary key.
 * @property name Human-readable label.
 * @property pipelineId Bound pipeline id, or `null` when the trigger is inert
 *   (unbound). No foreign key: a trigger may outlive its pipeline, and the fire
 *   path auto-disables a trigger whose pipeline has been deleted.
 * @property prompt Input message fed to the bound pipeline on each fire.
 * @property conditionJson The serialised [app.knotwork.android.domain.models.TriggerCondition].
 * @property enabled Whether the trigger is active (indexed).
 * @property createdAt Epoch-millis the trigger was created.
 * @property lastFiredAt Epoch-millis of the last fire, or `null` if never fired.
 */
@Entity(tableName = "triggers")
data class TriggerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pipelineId: String?,
    val prompt: String,
    val conditionJson: String,
    @ColumnInfo(index = true) val enabled: Boolean,
    val createdAt: Long,
    val lastFiredAt: Long?,
)
