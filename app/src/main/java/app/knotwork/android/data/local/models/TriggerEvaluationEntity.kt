package app.knotwork.android.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one trigger-evaluation journal record (`trigger_evaluations`
 * table, introduced in v51, HITL columns added in v56). See
 * [app.knotwork.android.domain.models.TriggerEvaluation] for the domain contract
 * and the no-silent-skips invariant.
 *
 * The sealed verdict and run-outcome are flattened into string discriminators
 * (plus a couple of payload columns) rather than a JSON blob, so the "by trigger,
 * by time" query and the run-outcome update both stay plain indexed SQL. The
 * discriminator strings mirror the enum / sealed names in the domain model and,
 * being persisted, must never be renamed.
 *
 * **No foreign key on [triggerId].** The journal is a diagnostic observer, not a
 * child of the trigger: it deliberately survives the trigger's deletion so a
 * post-mortem of "why did my automation stop firing before I removed it" stays
 * possible, and its growth is bounded by the retention pass
 * ([app.knotwork.android.domain.usecases.CleanupTriggerJournalUseCase]) rather
 * than by a cascade. This mirrors the FK-free convention of the other
 * loosely-coupled diagnostic tables (`pipeline_runs`, `pending_interactions`).
 *
 * @property id Stable unique id of this record (UUID), primary key.
 * @property triggerId Id of the evaluated trigger. Indexed together with
 *   [evaluatedAt] for the newest-first per-trigger journal query.
 * @property evaluatedAt Epoch-millis of the evaluation moment.
 * @property source Evaluation origin discriminator
 *   ([app.knotwork.android.domain.models.TriggerEvaluationSource] name).
 * @property verdictKind Verdict discriminator: `FIRED` / `RE_ARMED` / `SKIPPED`.
 * @property skipReason [app.knotwork.android.domain.models.TriggerSkipReason] name
 *   when [verdictKind] is `SKIPPED`; `null` otherwise.
 * @property runId Id of the enqueued run for a `FIRED` verdict; `null` otherwise.
 *   Indexed because the run-outcome write matches on it.
 * @property outcomeKind Terminal run-outcome discriminator (`SUCCESS` /
 *   `FAILURE` / `CANCELLED_BY_SYSTEM` / `HITL_TIMEOUT`), or `null` until the run
 *   settles (or when no run was started).
 * @property outcomeError Human-readable error for a `FAILURE` outcome; `null`
 *   otherwise.
 * @property hitlGateCount How many human-in-the-loop gates the run raised; `0`
 *   (the default for every pre-v56 row and for every run that never asked) means
 *   the domain record carries no
 *   [app.knotwork.android.domain.models.TriggerHitlActivity] at all.
 * @property hitlLastKind [app.knotwork.android.domain.models.PendingInteractionKind]
 *   name of the most recent gate; `null` while [hitlGateCount] is `0`.
 * @property hitlLastResolution
 *   [app.knotwork.android.domain.models.TriggerHitlResolution] name of the most
 *   recent gate; `null` while [hitlGateCount] is `0`.
 * @property hitlParked Whether the most recent gate outlived its live waiting
 *   phase and parked on a durable `pending_interactions` record — the column that
 *   makes a background approval answered from the shade provable after the fact.
 */
@Entity(
    tableName = "trigger_evaluations",
    indices = [
        Index(value = ["triggerId", "evaluatedAt"]),
        Index(value = ["runId"]),
    ],
)
data class TriggerEvaluationEntity(
    @PrimaryKey val id: String,
    val triggerId: String,
    val evaluatedAt: Long,
    val source: String,
    val verdictKind: String,
    val skipReason: String?,
    val runId: String?,
    val outcomeKind: String?,
    val outcomeError: String?,
    @ColumnInfo(defaultValue = "0")
    val hitlGateCount: Int = 0,
    val hitlLastKind: String? = null,
    val hitlLastResolution: String? = null,
    @ColumnInfo(defaultValue = "0")
    val hitlParked: Boolean = false,
)
