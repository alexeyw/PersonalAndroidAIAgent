package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import app.knotwork.android.data.local.models.OnboardingMilestoneEntity
import app.knotwork.android.data.local.models.UsageCounterEntity
import app.knotwork.android.data.local.models.UsagePipelineDayEntity
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
 * `usage_active_day`, `usage_pipeline_day`, `onboarding_milestone`).
 *
 * Counters are advanced through atomic SQLite `UPSERT` statements so concurrent
 * recorders cannot lose an increment, and active days are recorded with
 * `INSERT OR IGNORE` so the table stays a pure set. Onboarding markers use the
 * same `INSERT OR IGNORE`, which is what gives them their write-once (first
 * occurrence wins) semantics. Every read is exposed as a [Flow] so the Usage
 * statistics screen updates live. No method here touches the network — the whole
 * surface is on-device.
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
     * Records that [pipelineId] had activity on [day]. `INSERT OR IGNORE` makes a
     * repeat within the same day a no-op, so the table stays the set of
     * `(day, pipeline)` pairs rather than a second run counter.
     *
     * @param day Device-local active day as an ISO `yyyy-MM-dd` string.
     * @param pipelineId Id of the pipeline that ran.
     */
    @Query("INSERT OR IGNORE INTO usage_pipeline_day (day, pipelineId) VALUES (:day, :pipelineId)")
    suspend fun recordPipelineDay(day: String, pipelineId: String)

    /**
     * Records one terminal **root** run: bumps both the per-pipeline and the
     * per-outcome tally, marks [day] active and — when the pipeline was
     * resolved — marks that pipeline alive on [day], atomically.
     *
     * A run with an unresolved pipeline (the sentinel key) is deliberately left
     * out of the per-day set: it still counts as usage, but it cannot make any
     * particular pipeline "live".
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
        if (pipelineKey != UsageTelemetryCategories.NULL_PIPELINE_KEY) {
            recordPipelineDay(day, pipelineKey)
        }
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
     * Live projection of the recorded active days, oldest first.
     *
     * The whole set (rather than a `COUNT/MIN/MAX` aggregate) because the window,
     * streak and break figures need the individual days; the all-time count and
     * bounds are then derived from the same list, so the two readings of "active
     * days" cannot disagree.
     *
     * @return A [Flow] emitting every recorded ISO `yyyy-MM-dd` day on each change.
     */
    @Query("SELECT day FROM usage_active_day ORDER BY day ASC")
    fun observeActiveDays(): Flow<List<String>>

    /**
     * Live projection of the `(day, pipeline)` activity pairs from [minDay]
     * onwards — bounded in SQL because only the current window is ever consulted.
     *
     * @param minDay Inclusive lower bound as an ISO `yyyy-MM-dd` string (the ISO
     *   form sorts chronologically, so a plain string comparison is correct).
     * @return A [Flow] emitting the matching pairs on every change.
     */
    @Query("SELECT * FROM usage_pipeline_day WHERE day >= :minDay")
    fun observePipelineDaysSince(minDay: String): Flow<List<UsagePipelineDayEntity>>

    /**
     * Records an onboarding marker, keeping the **first** occurrence:
     * `INSERT OR IGNORE` makes a repeat on an already-recorded marker a no-op, so
     * a second pass through onboarding cannot move a measured journey.
     *
     * @param milestoneKey Stable `OnboardingMilestone.name`.
     * @param atMillis Wall-clock time of the marker, epoch-millis.
     * @param detail Optional marker payload (the scenario's pipeline id), or `null`.
     */
    @Query(
        "INSERT OR IGNORE INTO onboarding_milestone (milestoneKey, atMillis, detail) " +
            "VALUES (:milestoneKey, :atMillis, :detail)",
    )
    suspend fun recordMilestone(milestoneKey: String, atMillis: Long, detail: String?)

    /**
     * Live projection of every recorded onboarding marker.
     *
     * @return A [Flow] emitting the full marker list on every change.
     */
    @Query("SELECT * FROM onboarding_milestone")
    fun observeMilestones(): Flow<List<OnboardingMilestoneEntity>>

    /**
     * One-shot snapshot of the recorded onboarding markers, for the read-then-write
     * first-value attribution decision.
     *
     * @return Every marker row currently stored; empty when nothing was recorded.
     */
    @Query("SELECT * FROM onboarding_milestone")
    suspend fun getMilestones(): List<OnboardingMilestoneEntity>

    /** Deletes every counter row. */
    @Query("DELETE FROM usage_counter")
    suspend fun clearCounters()

    /** Deletes every active-day row. */
    @Query("DELETE FROM usage_active_day")
    suspend fun clearActiveDays()

    /** Deletes every `(day, pipeline)` activity row. */
    @Query("DELETE FROM usage_pipeline_day")
    suspend fun clearPipelineDays()

    /** Deletes every onboarding marker row. */
    @Query("DELETE FROM onboarding_milestone")
    suspend fun clearMilestones()

    /**
     * Clears all telemetry — counters, active days, per-day pipeline activity and
     * onboarding markers — in one transaction so a reset can never leave a
     * half-cleared store.
     */
    @Transaction
    suspend fun clearAll() {
        clearCounters()
        clearActiveDays()
        clearPipelineDays()
        clearMilestones()
    }
}
