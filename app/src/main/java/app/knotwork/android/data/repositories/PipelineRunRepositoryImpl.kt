package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.PipelineRunDao
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.PipelineRunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [PipelineRunRepository].
 *
 * Maps between the domain `PipelineRun` and the persistence
 * [PipelineRunEntity] (enums stored as their `name` strings) and routes all
 * DAO calls through [Dispatchers.IO]. The terminal-status guard required by
 * the repository contract is implemented in SQL — every mutating DAO query
 * carries a `status NOT IN (terminal)` clause — so the guard holds even when
 * two writers race on different coroutines.
 *
 * **Best-effort contract.** Every method absorbs storage and mapping failures
 * (logged, neutral result) per the interface contract: run records must never
 * take down the execution they describe, nor brick app startup when the table
 * holds an unreadable row. `CancellationException` is always re-thrown.
 *
 * **Process ownership.** [liveRunIds] records every run id created by this
 * process (the class is a process-wide `@Singleton`). [getOrphanedRuns]
 * filters those ids out, which is what makes the startup orphan sweep safe to
 * run at any time: a run still executing in this process — kept alive by the
 * foreground service or a WorkManager worker while no Activity exists — can
 * never be mistaken for an orphan of a dead process.
 */
@Singleton
class PipelineRunRepositoryImpl @Inject constructor(private val pipelineRunDao: PipelineRunDao) :
    PipelineRunRepository {

    /**
     * Ids of runs created by the current process. Membership means the run's
     * in-memory machinery (queue worker, suspension deferreds) is — or was —
     * hosted here, so the orphan sweep must not touch the record. The set
     * dies with the process, exactly matching the ownership semantics.
     */
    private val liveRunIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun createRun(run: PipelineRun) {
        // Register ownership before the insert: even if the write fails, the
        // id is process-owned and must be invisible to the orphan sweep.
        liveRunIds.add(run.id)
        absorbing("createRun") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.insertRun(run.toEntity())
            }
        }
    }

    override suspend fun markRunning(runId: String, pipelineId: String, graphContentHash: String) {
        absorbing("markRunning") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.markRunning(
                    runId = runId,
                    status = PipelineRunStatus.RUNNING.name,
                    pipelineId = pipelineId,
                    graphContentHash = graphContentHash,
                    terminalStatuses = TERMINAL_STATUS_NAMES,
                )
            }
        }
    }

    override suspend fun updateStatus(runId: String, status: PipelineRunStatus) {
        absorbing("updateStatus") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.updateStatus(
                    runId = runId,
                    status = status.name,
                    terminalStatuses = TERMINAL_STATUS_NAMES,
                )
            }
        }
    }

    override suspend fun updateCurrentNode(runId: String, nodeId: String) {
        absorbing("updateCurrentNode") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.updateCurrentNode(
                    runId = runId,
                    nodeId = nodeId,
                    terminalStatuses = TERMINAL_STATUS_NAMES,
                )
            }
        }
    }

    override suspend fun finishRun(runId: String, status: PipelineRunStatus, errorMessage: String?) {
        // Caller contract violation, not a storage failure — never absorbed.
        require(status.isTerminal) { "finishRun requires a terminal status, got $status" }
        absorbing("finishRun") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.finishRun(
                    runId = runId,
                    status = status.name,
                    finishedAt = System.currentTimeMillis(),
                    errorMessage = errorMessage,
                    terminalStatuses = TERMINAL_STATUS_NAMES,
                )
            }
        }
    }

    override suspend fun getRun(runId: String): PipelineRun? = absorbing("getRun") {
        withContext(Dispatchers.IO) {
            pipelineRunDao.getRun(runId)?.toDomain()
        }
    }

    override suspend fun markResumed(runId: String, fromStatus: PipelineRunStatus): Boolean {
        // Re-register ownership before the transition for the same reason
        // createRun does: from this moment the run's machinery lives in this
        // process, and a failed write must still keep the id invisible to
        // the orphan sweep.
        liveRunIds.add(runId)
        return absorbing("markResumed") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.markResumed(
                    runId = runId,
                    fromStatus = fromStatus.name,
                    toStatus = PipelineRunStatus.QUEUED.name,
                ) == 1
            }
        } ?: false
    }

    override suspend fun getActiveRunForSession(sessionId: String): PipelineRun? = absorbing("getActiveRunForSession") {
        withContext(Dispatchers.IO) {
            pipelineRunDao.getActiveRunForSession(sessionId, ACTIVE_STATUS_NAMES)?.toDomain()
        }
    }

    override suspend fun getLatestRunForSession(sessionId: String): PipelineRun? = absorbing("getLatestRunForSession") {
        withContext(Dispatchers.IO) {
            pipelineRunDao.getLatestRunForSession(sessionId)?.toDomain()
        }
    }

    override fun observeRunsForSession(sessionId: String): Flow<List<PipelineRun>> =
        pipelineRunDao.observeRunsForSession(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Pipeline-run store failure in observeRunsForSession; degrading to empty")
                emit(emptyList())
            }

    override fun observeActiveRunSessionIds(): Flow<Set<String>> =
        pipelineRunDao.observeSessionIdsByStatuses(ACTIVE_STATUS_NAMES)
            .map { it.toSet() }
            // Room re-runs the query on every table write (per-node progress
            // included); the set only changes when a run starts or settles,
            // so deduplicate here instead of in every consumer.
            .distinctUntilChanged()
            .catch { e ->
                Timber.e(e, "Pipeline-run store failure in observeActiveRunSessionIds; degrading to empty")
                emit(emptySet())
            }

    override suspend fun discardInterruptedRun(runId: String) {
        absorbing("discardInterruptedRun") {
            withContext(Dispatchers.IO) {
                pipelineRunDao.discardInterruptedRun(
                    runId = runId,
                    fromStatus = PipelineRunStatus.INTERRUPTED.name,
                    toStatus = PipelineRunStatus.FAILED.name,
                    errorMessage = DISCARDED_BY_USER_MESSAGE,
                )
            }
        }
    }

    override suspend fun getOrphanedRuns(): List<PipelineRun> = absorbing("getOrphanedRuns") {
        withContext(Dispatchers.IO) {
            pipelineRunDao.getRunsByStatuses(ACTIVE_STATUS_NAMES)
                .filter { it.id !in liveRunIds }
                .map { it.toDomain() }
        }
    } ?: emptyList()

    /**
     * Runs [block] under the best-effort contract via the shared
     * [absorbingStoreFailure] helper, branding the log line with the
     * pipeline-run store prefix.
     *
     * @param operation Name used in the failure log line.
     * @param block The storage operation to attempt.
     * @return The block's result, or `null` when the store failed.
     */
    private suspend fun <T> absorbing(operation: String, block: suspend () -> T): T? =
        absorbingStoreFailure({ "Pipeline-run store failure in $operation; continuing without it" }, block)

    private companion object {
        /** Terminal status names used as the SQL `NOT IN` overwrite guard. */
        val TERMINAL_STATUS_NAMES: List<String> =
            PipelineRunStatus.entries.filter { it.isTerminal }.map { it.name }

        /** Non-terminal status names: active-run lookup and orphan-sweep scope. */
        val ACTIVE_STATUS_NAMES: List<String> =
            PipelineRunStatus.entries.filterNot { it.isTerminal }.map { it.name }

        /** Error message stamped on a run the user explicitly discarded instead of resuming. */
        const val DISCARDED_BY_USER_MESSAGE: String = "Discarded by user"
    }
}

/**
 * Maps the domain run to its persistence entity, storing enums as `name` strings.
 *
 * @return The entity ready for insertion.
 */
private fun PipelineRun.toEntity(): PipelineRunEntity = PipelineRunEntity(
    id = id,
    sessionId = sessionId,
    pipelineId = pipelineId,
    origin = origin.name,
    status = status.name,
    currentNodeId = currentNodeId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    errorMessage = errorMessage,
    graphContentHash = graphContentHash,
    userPrompt = userPrompt,
)

/**
 * Maps the persistence entity back to the domain run. Enum columns are parsed
 * strictly ([IllegalArgumentException] on unknown names) — an unreadable row
 * is data corruption; the repository's best-effort wrapper turns it into a
 * logged degraded read instead of a crash.
 *
 * @return The domain model.
 */
private fun PipelineRunEntity.toDomain(): PipelineRun = PipelineRun(
    id = id,
    sessionId = sessionId,
    pipelineId = pipelineId,
    origin = RunOrigin.valueOf(origin),
    status = PipelineRunStatus.valueOf(status),
    currentNodeId = currentNodeId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    errorMessage = errorMessage,
    graphContentHash = graphContentHash,
    userPrompt = userPrompt,
)
