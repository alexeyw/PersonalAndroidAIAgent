package app.knotwork.android.data.local.models

import androidx.room.Entity

/**
 * Room entity recording that a pipeline had activity on a given device-local
 * calendar day — the one time-resolved dimension of the local usage telemetry.
 *
 * The plain counters ([UsageCounterEntity]) are all-time totals with no date, so
 * "how many pipelines are alive *this week*" cannot be answered from them; this
 * table adds exactly the missing `(day, pipeline)` dimension and nothing more.
 * Exactly one row per pair (both columns form the primary key), written via
 * `INSERT OR IGNORE`, so the table holds a **set** — never per-run timestamps,
 * and never how many times a pipeline ran that day (the all-time tally already
 * carries volume). Rows are only written for runs whose pipeline was resolved.
 *
 * Size is bounded by `active days × pipelines used` (order of hundreds of rows a
 * year), so the table needs no retention pass of its own. Stored in the
 * SQLCipher-encrypted database; never transmitted.
 *
 * Added in schema v57 (`MIGRATION_56_57`).
 *
 * @property day Device-local calendar day as an ISO `yyyy-MM-dd` string.
 * @property pipelineId Id of the pipeline that had a terminal root run that day.
 */
@Entity(tableName = "usage_pipeline_day", primaryKeys = ["day", "pipelineId"])
data class UsagePipelineDayEntity(val day: String, val pipelineId: String)
