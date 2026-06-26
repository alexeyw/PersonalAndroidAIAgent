package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import app.knotwork.android.data.local.models.UsageCounterEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stable counter-family keys and sentinels for the local usage-telemetry tables.
 * Shared by [UsageTelemetryDao] (writes) and the repository (reads/partitioning)
 * so the two cannot drift. These strings are **persisted** as counter rows, so
 * they must never be renamed.
 */
object UsageTelemetryCategories {
    /** Per-pipeline terminal-root-run tally; key = pipeline id (or [NULL_PIPELINE_KEY]). */
    const val PIPELINE_RUN: String = "pipeline_run"

    /** Per-outcome terminal-root-run tally; key = `PipelineRunStatus.name`. */
    const val RUN_OUTCOME: String = "run_outcome"

    /** Per-kind trigger-firing tally; key = `TriggerCondition.telemetryKind`. */
    const val TRIGGER_FIRE: String = "trigger_fire"

    /**
     * Sentinel [PIPELINE_RUN] key for a run whose pipeline was never resolved
     * (e.g. a queued run swept to `INTERRUPTED`). The empty string never
     * collides with a real pipeline UUID.
     */
    const val NULL_PIPELINE_KEY: String = ""
}

/**
 * Data Access Object for the local usage-telemetry tables (`usage_counter`,
 * `usage_active_day`).
 *
 * Counters are advanced through atomic SQLite `UPSERT` statements so concurrent
 * recorders cannot lose an increment, and active days are recorded with
 * `INSERT OR IGNORE` so the table stays a pure set. Every read is exposed as a
 * [Flow] so the Usage statistics screen updates live. No method here touches the
 * network — the whole surface is on-device.
 */
@Dao
interface UsageTelemetryDao {

    /**
     * Atomically increments the `(category, counterKey)` counter, inserting it
     * at `1` when absent. The SQLite `ON CONFLICT … DO UPDATE` UPSERT keeps the
     * increment race-free across coroutines.
     *
     * @param category The counter family (see [UsageTelemetryCategories]).
     * @param counterKey The within-family key (e.g. a `PipelineRunStatus.name`).
     */
    @Query(
        "INSERT INTO usage_counter (category, counterKey, count) VALUES (:category, :counterKey, 1) " +
            "ON CONFLICT(category, counterKey) DO UPDATE SET count = count + 1",
    )
    suspend fun incrementCounter(category: String, counterKey: String)

    /**
     * Records [day] as active. `INSERT OR IGNORE` makes a repeat on an
     * already-recorded day a no-op, so the table holds the set of active days.
     *
     * @param day Device-local active day as an ISO `yyyy-MM-dd` string.
     */
    @Query("INSERT OR IGNORE INTO usage_active_day (day) VALUES (:day)")
    suspend fun recordActiveDay(day: String)

    /**
     * Records one terminal **root** run: bumps both the per-pipeline and the
     * per-outcome tally and marks [day] active, atomically.
     *
     * @param pipelineKey Pipeline id, or [UsageTelemetryCategories.NULL_PIPELINE_KEY].
     * @param statusName Terminal `PipelineRunStatus.name`.
     * @param day Device-local active day as an ISO `yyyy-MM-dd` string.
     */
    @Transaction
    suspend fun recordRun(pipelineKey: String, statusName: String, day: String) {
        incrementCounter(UsageTelemetryCategories.PIPELINE_RUN, pipelineKey)
        incrementCounter(UsageTelemetryCategories.RUN_OUTCOME, statusName)
        recordActiveDay(day)
    }

    /**
     * Records one background trigger firing: bumps the per-kind tally and marks
     * [day] active, atomically.
     *
     * @param kind Stable `TriggerCondition.telemetryKind`.
     * @param day Device-local active day as an ISO `yyyy-MM-dd` string.
     */
    @Transaction
    suspend fun recordTriggerFire(kind: String, day: String) {
        incrementCounter(UsageTelemetryCategories.TRIGGER_FIRE, kind)
        recordActiveDay(day)
    }

    /**
     * Live projection of every counter row across all families.
     *
     * @return A [Flow] emitting the full counter list on every change.
     */
    @Query("SELECT * FROM usage_counter")
    fun observeCounters(): Flow<List<UsageCounterEntity>>

    /**
     * Live projection of the active-day aggregate (count, earliest, latest).
     *
     * @return A [Flow] emitting the [ActiveDayStats]; on an empty table the
     *   count is `0` and both bounds are `null`.
     */
    @Query("SELECT COUNT(*) AS dayCount, MIN(day) AS firstDay, MAX(day) AS lastDay FROM usage_active_day")
    fun observeActiveDayStats(): Flow<ActiveDayStats>

    /** Deletes every counter row. */
    @Query("DELETE FROM usage_counter")
    suspend fun clearCounters()

    /** Deletes every active-day row. */
    @Query("DELETE FROM usage_active_day")
    suspend fun clearActiveDays()

    /**
     * Clears all telemetry — counters and active days — in one transaction so a
     * reset can never leave a half-cleared store.
     */
    @Transaction
    suspend fun clearAll() {
        clearCounters()
        clearActiveDays()
    }
}

/**
 * Aggregate projection of the `usage_active_day` table.
 *
 * @property dayCount Number of distinct active days.
 * @property firstDay Earliest active day (ISO `yyyy-MM-dd`), or `null` when empty.
 * @property lastDay Latest active day (ISO `yyyy-MM-dd`), or `null` when empty.
 */
data class ActiveDayStats(val dayCount: Int, val firstDay: String?, val lastDay: String?)
