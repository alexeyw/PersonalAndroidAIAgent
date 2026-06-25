package app.knotwork.android.data.local.models

import androidx.room.Entity

/**
 * Room entity for a single monotonic usage counter backing the privacy-preserving
 * local telemetry feature.
 *
 * A generic `(category, counterKey) → count` row so the small fixed set of
 * tallies shares one narrow table:
 * - `category = "pipeline_run"`, `counterKey = pipelineId` — terminal root runs
 *   per pipeline (an unresolved pipeline id is stored as the empty-string
 *   sentinel, which never collides with a real UUID id).
 * - `category = "run_outcome"`, `counterKey = PipelineRunStatus.name` — terminal
 *   root runs per outcome.
 * - `category = "trigger_fire"`, `counterKey = TriggerCondition.telemetryKind` —
 *   background trigger firings per kind.
 *
 * All counts live in the SQLCipher-encrypted database and never leave the
 * device. Added in schema v47 (`MIGRATION_46_47`).
 *
 * @property category The counter family (see above).
 * @property counterKey The within-family key (pipeline id / status / trigger kind).
 * @property count Monotonic occurrence count; incremented via an atomic SQL UPSERT.
 */
@Entity(tableName = "usage_counter", primaryKeys = ["category", "counterKey"])
data class UsageCounterEntity(val category: String, val counterKey: String, val count: Int)
