package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.local.dao.ExternalAutomationJournalDao
import app.knotwork.android.data.local.models.ExternalAutomationRequestEntity
import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
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
 * Room-backed [ExternalAutomationJournalRepository].
 *
 * Flattens the sealed [ExternalAutomationStatus] and [ExternalAutomationTarget]
 * into the string discriminators of [ExternalAutomationRequestEntity] on write and
 * reconstitutes them on read. A row whose stored discriminators cannot be decoded
 * is **skipped on read** rather than crashing the journal load, mirroring
 * [TriggerJournalRepositoryImpl] — one unreadable row must not hide the rest of
 * the history.
 *
 * **Best-effort observer, with one deliberate exception.** Every read degrades to
 * empty and the outcome/refusal writes absorb storage failures, so a journal
 * defect can never disturb the run it observes. [admitAcceptedWithinCeiling] is the
 * exception: it reports failure as "not admitted", because the alternative — an
 * unreadable database silently lifting the rate ceiling — would turn a storage
 * defect into an unbounded entry point for third-party code.
 *
 * @property dao The journal DAO.
 */
@Singleton
class ExternalAutomationJournalRepositoryImpl @Inject constructor(private val dao: ExternalAutomationJournalDao) :
    ExternalAutomationJournalRepository {

    /** Dispatcher carrying every DAO call. Swapped in unit tests. */
    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun recordRefusal(entry: ExternalAutomationJournalEntry) {
        require(entry.status is ExternalAutomationStatus.Rejected || entry.status is ExternalAutomationStatus.Blocked) {
            "recordRefusal requires a refusal status, got ${entry.status}"
        }
        absorbingStoreFailure({ "External-automation journal recordRefusal failed; ignored" }) {
            withContext(dispatcher) { dao.recordRefusal(entry.toEntity()) }
        }
    }

    override suspend fun admitAcceptedWithinCeiling(
        entry: ExternalAutomationJournalEntry,
        windowStartEpochMs: Long,
        limitPerWindow: Int,
    ): Boolean {
        // Caller contract violations, not storage failures — never absorbed. A
        // non-positive limit would admit nothing while reading like a ceiling, and
        // an admission without a run id is uncountable by the ceiling that governs
        // it (the count predicate is `runId IS NOT NULL`).
        require(limitPerWindow > 0) { "limitPerWindow must be positive, was $limitPerWindow" }
        require(entry.runId != null) { "An admitted request must carry its pre-minted run id" }
        val admitted = absorbingStoreFailure(
            { "External-automation journal admission failed; refusing the request" },
        ) {
            withContext(dispatcher) {
                dao.insertIfUnderCeiling(entry.toEntity(), windowStartEpochMs, limitPerWindow)
            }
        }
        // `null` is an absorbed storage failure. Fail closed: an entry point whose
        // write rate is set by another app must not be admitted on the strength of
        // a ledger we could not write to.
        return admitted == true
    }

    override suspend fun recordOutcome(runId: String, status: ExternalAutomationStatus) {
        absorbingStoreFailure({ "External-automation journal recordOutcome failed; ignored" }) {
            withContext(dispatcher) { dao.updateStatus(runId, status.discriminator()) }
        }
    }

    override suspend fun findByRunId(runId: String): ExternalAutomationJournalEntry? = absorbingStoreFailure(
        { "External-automation journal findByRunId failed; treating as absent" },
    ) {
        withContext(dispatcher) { dao.findByRunId(runId)?.toDomainOrNull() }
    }

    override fun observeAll(): Flow<List<ExternalAutomationJournalEntry>> = dao.observeAll()
        .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
        // Degrade to an empty journal on a DAO/SQLCipher read error rather than
        // letting the exception cancel the screen's collector.
        .catch { e ->
            Timber.tag(TAG).w(e, "External-automation journal read failed; emitting empty.")
            emit(emptyList())
        }
        .flowOn(dispatcher)

    override suspend fun applyRetention(olderThanEpochMs: Long, maxRecords: Int): Int {
        // Fail fast on a misconfigured cap: `maxRecords <= 0` makes the enforce-cap
        // DELETE keep zero rows and wipe the journal. A programming error, not a
        // storage failure, so it is asserted rather than absorbed.
        require(maxRecords > 0) { "maxRecords must be positive, was $maxRecords" }
        return absorbingStoreFailure({ "External-automation journal retention pass failed; ignored" }) {
            withContext(dispatcher) { dao.applyRetention(olderThanEpochMs, maxRecords) }
        } ?: 0
    }

    /** Maps a domain entry to its persisted row. */
    private fun ExternalAutomationJournalEntry.toEntity(): ExternalAutomationRequestEntity =
        ExternalAutomationRequestEntity(
            id = id,
            requestId = requestId,
            receivedAt = receivedAt,
            action = action,
            targetKind = target?.kindDiscriminator(),
            targetValue = target?.value(),
            declaredReturnPackage = declaredReturnPackage,
            returnAction = returnAction,
            attestedSenderPackage = attestedSenderPackage,
            statusKind = status.discriminator(),
            statusReason = status.reasonOrNull()?.name,
            runId = runId,
            repeatCount = repeatCount,
        )

    /**
     * Maps a persisted row back to its domain entry, or `null` when a stored
     * discriminator cannot be decoded.
     *
     * @return The decoded entry, or `null` for an unreadable row.
     */
    private fun ExternalAutomationRequestEntity.toDomainOrNull(): ExternalAutomationJournalEntry? {
        val status = decodeStatus(statusKind, statusReason) ?: return null
        val target = decodeTarget(targetKind, targetValue)
        // A row that named a target but whose kind is undecodable would silently
        // read as "named nothing" — a different, and materially misleading, event.
        if (targetKind != null && target == null) return null
        return ExternalAutomationJournalEntry(
            id = id,
            requestId = requestId,
            receivedAt = receivedAt,
            action = action,
            target = target,
            declaredReturnPackage = declaredReturnPackage,
            returnAction = returnAction,
            attestedSenderPackage = attestedSenderPackage,
            status = status,
            runId = runId,
            repeatCount = repeatCount,
        )
    }

    private companion object {
        /** Timber tag for the journal's own diagnostics. */
        const val TAG = "ExternalAutomationJournal"

        /** Target discriminator for a request that named its pipeline by id. */
        const val TARGET_KIND_ID = "ID"

        /** Target discriminator for a request that named its pipeline by name. */
        const val TARGET_KIND_NAME = "NAME"

        /**
         * The persisted status discriminator. Deliberately the domain name rather
         * than a mapping table: these five strings are published in the
         * third-party contract, so the wire value, the journal value and the
         * sealed member are one vocabulary and cannot drift.
         *
         * @return The discriminator for this status.
         */
        fun ExternalAutomationStatus.discriminator(): String = when (this) {
            ExternalAutomationStatus.Accepted -> "Accepted"
            ExternalAutomationStatus.Completed -> "Completed"
            ExternalAutomationStatus.Failed -> "Failed"
            is ExternalAutomationStatus.Rejected -> "Rejected"
            is ExternalAutomationStatus.Blocked -> "Blocked"
        }

        /**
         * The refusal reason a status carries, if any.
         *
         * @return The reason for a refusal status, `null` for the other three.
         */
        fun ExternalAutomationStatus.reasonOrNull(): ExternalAutomationRejectionReason? = when (this) {
            is ExternalAutomationStatus.Rejected -> reason
            is ExternalAutomationStatus.Blocked -> reason
            else -> null
        }

        /**
         * Rebuilds a status from its stored discriminators.
         *
         * A refusal whose reason is missing or unknown decodes to `null` rather
         * than to a refusal with an invented reason: the reason is the whole
         * content of a refusal row, and guessing it would put words in the app's
         * mouth on a surface the user reads to understand a security decision.
         *
         * @param kind Stored status discriminator.
         * @param reason Stored reason discriminator, if any.
         * @return The decoded status, or `null` when the row cannot be read.
         */
        fun decodeStatus(kind: String, reason: String?): ExternalAutomationStatus? = when (kind) {
            "Accepted" -> ExternalAutomationStatus.Accepted
            "Completed" -> ExternalAutomationStatus.Completed
            "Failed" -> ExternalAutomationStatus.Failed
            "Rejected" -> decodeReason(reason)?.let { ExternalAutomationStatus.Rejected(it) }
            "Blocked" -> decodeReason(reason)?.let { ExternalAutomationStatus.Blocked(it) }
            else -> null
        }

        /**
         * Decodes a stored refusal reason.
         *
         * @param reason Stored reason discriminator.
         * @return The reason, or `null` when absent or unknown.
         */
        fun decodeReason(reason: String?): ExternalAutomationRejectionReason? =
            ExternalAutomationRejectionReason.entries.firstOrNull { it.name == reason }

        /**
         * The persisted discriminator of how a request named its target.
         *
         * @return `ID` or `NAME`.
         */
        fun ExternalAutomationTarget.kindDiscriminator(): String = when (this) {
            is ExternalAutomationTarget.ById -> TARGET_KIND_ID
            is ExternalAutomationTarget.ByName -> TARGET_KIND_NAME
        }

        /**
         * The pipeline id or name the request carried.
         *
         * @return The target's payload.
         */
        fun ExternalAutomationTarget.value(): String = when (this) {
            is ExternalAutomationTarget.ById -> pipelineId
            is ExternalAutomationTarget.ByName -> pipelineName
        }

        /**
         * Rebuilds the request's target from its stored columns.
         *
         * @param kind Stored target-kind discriminator, or `null` when the request
         *   named no target at all.
         * @param value Stored target payload.
         * @return The target, or `null` when none was stored or the kind is unknown.
         */
        fun decodeTarget(kind: String?, value: String?): ExternalAutomationTarget? = when {
            kind == null || value == null -> null
            kind == TARGET_KIND_ID -> ExternalAutomationTarget.ById(value)
            kind == TARGET_KIND_NAME -> ExternalAutomationTarget.ByName(value)
            else -> null
        }
    }
}
