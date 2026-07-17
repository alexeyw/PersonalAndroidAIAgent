package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.local.dao.TriggerJournalDao
import app.knotwork.android.data.local.models.TriggerEvaluationEntity
import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.models.TriggerSkipReason
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [TriggerJournalRepository].
 *
 * Flattens the sealed [TriggerEvaluationVerdict] / [TriggerRunOutcome] into the
 * string discriminators of [TriggerEvaluationEntity] on write and reconstitutes
 * them on read. A row whose stored discriminators cannot be decoded (an unknown
 * future source or verdict, or a corrupt value) is **skipped on read** rather
 * than crashing the journal load — mirroring [TriggerRepositoryImpl] — so one bad
 * row never hides the rest of a trigger's history.
 *
 * **Best-effort observer.** Every write goes through [absorbingStoreFailure] and
 * reads degrade to an empty list on any DAO/SQLCipher error, so a journal storage
 * failure can never propagate into (and abort) the trigger evaluation or run it
 * merely observes. `CancellationException` is always re-thrown by the wrapper.
 *
 * @property dao The journal DAO.
 */
@Singleton
class TriggerJournalRepositoryImpl @Inject constructor(private val dao: TriggerJournalDao) : TriggerJournalRepository {

    /** Dispatcher carrying every DAO call. Swapped in unit tests. */
    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun recordEvaluation(evaluation: TriggerEvaluation) {
        absorbingStoreFailure({ "Trigger-journal recordEvaluation failed; ignored" }) {
            withContext(dispatcher) { dao.insert(evaluation.toEntity()) }
        }
    }

    override suspend fun recordRunOutcome(runId: String, outcome: TriggerRunOutcome) {
        val (kind, error) = outcome.encode()
        absorbingStoreFailure({ "Trigger-journal recordRunOutcome failed; ignored" }) {
            withContext(dispatcher) { dao.updateOutcome(runId, kind, error) }
        }
    }

    override fun observeByTrigger(triggerId: String): Flow<List<TriggerEvaluation>> = dao.observeByTrigger(triggerId)
        .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
        // Degrade to an empty journal on a DAO/SQLCipher read error rather
        // than letting the exception cancel the screen's collector.
        .catch { e ->
            Timber.tag(TAG).w(e, "Trigger-journal read failed for %s; emitting empty.", triggerId)
            emit(emptyList())
        }
        .flowOn(dispatcher)

    override suspend fun applyRetention(olderThanEpochMs: Long, maxRecords: Int): Int {
        // Fail fast on a misconfigured cap: `maxRecords <= 0` would make the
        // enforce-cap DELETE keep zero rows and wipe the whole journal. This is a
        // programming error, not a storage failure, so it is asserted rather than
        // absorbed.
        require(maxRecords > 0) { "maxRecords must be positive, was $maxRecords" }
        return absorbingStoreFailure({ "Trigger-journal retention pass failed; ignored" }) {
            withContext(dispatcher) { dao.applyRetention(olderThanEpochMs, maxRecords) }
        } ?: 0
    }

    /** Maps a domain [TriggerEvaluation] to its persisted row. */
    private fun TriggerEvaluation.toEntity(): TriggerEvaluationEntity {
        val (verdictKind, skipReason) = verdict.encode()
        val (outcomeKind, outcomeError) = outcome?.encode() ?: (null to null)
        return TriggerEvaluationEntity(
            id = id,
            triggerId = triggerId,
            evaluatedAt = evaluatedAt,
            source = source.name,
            verdictKind = verdictKind,
            skipReason = skipReason,
            runId = runId,
            outcomeKind = outcomeKind,
            outcomeError = outcomeError,
        )
    }

    /**
     * Maps a row to its domain model, or `null` when a core discriminator (the
     * [source] or the [verdict]) is undecodable — the caller drops such rows.
     */
    private fun TriggerEvaluationEntity.toDomainOrNull(): TriggerEvaluation? {
        val decodedSource = enumOrNull<TriggerEvaluationSource>(source)
        if (decodedSource == null) {
            Timber.tag(TAG).w("Skipping journal row %s: unknown source '%s'.", id, source)
            return null
        }
        val decodedVerdict = decodeVerdict(verdictKind, skipReason)
        if (decodedVerdict == null) {
            Timber.tag(TAG).w("Skipping journal row %s: undecodable verdict '%s'.", id, verdictKind)
            return null
        }
        return TriggerEvaluation(
            id = id,
            triggerId = triggerId,
            evaluatedAt = evaluatedAt,
            source = decodedSource,
            verdict = decodedVerdict,
            runId = runId,
            outcome = decodeOutcome(outcomeKind, outcomeError),
        )
    }

    /** Encodes a verdict into its (kind, skipReason) column pair. */
    private fun TriggerEvaluationVerdict.encode(): Pair<String, String?> = when (this) {
        TriggerEvaluationVerdict.Fired -> VERDICT_FIRED to null
        TriggerEvaluationVerdict.ReArmed -> VERDICT_RE_ARMED to null
        is TriggerEvaluationVerdict.Skipped -> VERDICT_SKIPPED to reason.name
    }

    /** Reconstitutes a verdict from its columns, or `null` when undecodable. */
    private fun decodeVerdict(kind: String, skipReason: String?): TriggerEvaluationVerdict? = when (kind) {
        VERDICT_FIRED -> TriggerEvaluationVerdict.Fired
        VERDICT_RE_ARMED -> TriggerEvaluationVerdict.ReArmed
        VERDICT_SKIPPED -> enumOrNull<TriggerSkipReason>(skipReason)?.let { TriggerEvaluationVerdict.Skipped(it) }
        else -> null
    }

    /** Encodes a settled run outcome into its (kind, error) column pair. */
    private fun TriggerRunOutcome.encode(): Pair<String, String?> = when (this) {
        TriggerRunOutcome.Success -> OUTCOME_SUCCESS to null
        is TriggerRunOutcome.Failure -> OUTCOME_FAILURE to error
        TriggerRunOutcome.CancelledBySystem -> OUTCOME_CANCELLED to null
        TriggerRunOutcome.HitlTimeout -> OUTCOME_HITL_TIMEOUT to null
    }

    /**
     * Reconstitutes a run outcome from its columns. A `null` [kind] (or an
     * unrecognised one) reads back as `null` — "no settled outcome yet". A
     * `FAILURE` kind whose [error] column is `null` is a corrupt half-written row
     * (writes always pair `FAILURE` with a message, even an empty one), so it too
     * reads back as `null` rather than being reported as a blank-reason failure —
     * this keeps a genuinely empty message (`error == ""`) distinguishable from
     * corruption.
     */
    private fun decodeOutcome(kind: String?, error: String?): TriggerRunOutcome? = when (kind) {
        OUTCOME_SUCCESS -> TriggerRunOutcome.Success
        OUTCOME_FAILURE -> error?.let { TriggerRunOutcome.Failure(it) }
        OUTCOME_CANCELLED -> TriggerRunOutcome.CancelledBySystem
        OUTCOME_HITL_TIMEOUT -> TriggerRunOutcome.HitlTimeout
        else -> null
    }

    private companion object {
        const val TAG = "TriggerJournal"

        const val VERDICT_FIRED = "FIRED"
        const val VERDICT_RE_ARMED = "RE_ARMED"
        const val VERDICT_SKIPPED = "SKIPPED"

        const val OUTCOME_SUCCESS = "SUCCESS"
        const val OUTCOME_FAILURE = "FAILURE"
        const val OUTCOME_CANCELLED = "CANCELLED_BY_SYSTEM"
        const val OUTCOME_HITL_TIMEOUT = "HITL_TIMEOUT"
    }
}

/**
 * Resolves [name] to an entry of enum [T] by its stable [Enum.name], or `null`
 * when [name] is `null` or matches no entry — the tolerant decode used for
 * persisted discriminators.
 */
private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
