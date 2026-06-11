package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.PipelineRunDao
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.PipelineRunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
 */
@Singleton
class PipelineRunRepositoryImpl @Inject constructor(private val pipelineRunDao: PipelineRunDao) :
    PipelineRunRepository {

    override suspend fun createRun(run: PipelineRun) = withContext(Dispatchers.IO) {
        pipelineRunDao.insertRun(run.toEntity())
    }

    override suspend fun markRunning(runId: String, pipelineId: String, graphContentHash: String) =
        withContext(Dispatchers.IO) {
            pipelineRunDao.markRunning(
                runId = runId,
                status = PipelineRunStatus.RUNNING.name,
                pipelineId = pipelineId,
                graphContentHash = graphContentHash,
                terminalStatuses = TERMINAL_STATUS_NAMES,
            )
        }

    override suspend fun updateStatus(runId: String, status: PipelineRunStatus) = withContext(Dispatchers.IO) {
        pipelineRunDao.updateStatus(
            runId = runId,
            status = status.name,
            terminalStatuses = TERMINAL_STATUS_NAMES,
        )
    }

    override suspend fun updateCurrentNode(runId: String, nodeId: String) = withContext(Dispatchers.IO) {
        pipelineRunDao.updateCurrentNode(
            runId = runId,
            nodeId = nodeId,
            terminalStatuses = TERMINAL_STATUS_NAMES,
        )
    }

    override suspend fun finishRun(runId: String, status: PipelineRunStatus, errorMessage: String?) =
        withContext(Dispatchers.IO) {
            require(status.isTerminal) { "finishRun requires a terminal status, got $status" }
            pipelineRunDao.finishRun(
                runId = runId,
                status = status.name,
                finishedAt = System.currentTimeMillis(),
                errorMessage = errorMessage,
                terminalStatuses = TERMINAL_STATUS_NAMES,
            )
        }

    override suspend fun getActiveRunForSession(sessionId: String): PipelineRun? = withContext(Dispatchers.IO) {
        pipelineRunDao.getActiveRunForSession(sessionId, ACTIVE_STATUS_NAMES)?.toDomain()
    }

    override fun observeRunsForSession(sessionId: String): Flow<List<PipelineRun>> =
        pipelineRunDao.observeRunsForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getOrphanedRunning(): List<PipelineRun> = withContext(Dispatchers.IO) {
        pipelineRunDao.getRunsByStatuses(ORPHAN_SWEEP_STATUS_NAMES).map { it.toDomain() }
    }

    override suspend fun deleteRunsForSession(sessionId: String) = withContext(Dispatchers.IO) {
        pipelineRunDao.deleteRunsForSession(sessionId)
    }

    private companion object {
        /** Terminal status names used as the SQL `NOT IN` overwrite guard. */
        val TERMINAL_STATUS_NAMES: List<String> =
            PipelineRunStatus.entries.filter { it.isTerminal }.map { it.name }

        /** Non-terminal status names matched by the active-run lookup. */
        val ACTIVE_STATUS_NAMES: List<String> =
            PipelineRunStatus.entries.filterNot { it.isTerminal }.map { it.name }

        /**
         * Statuses swept to INTERRUPTED at application start. WAITING_* runs
         * are excluded — the background-HITL flow owns their fate.
         */
        val ORPHAN_SWEEP_STATUS_NAMES: List<String> = listOf(
            PipelineRunStatus.QUEUED.name,
            PipelineRunStatus.RUNNING.name,
        )
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
)

/**
 * Maps the persistence entity back to the domain run. Enum columns are parsed
 * strictly — the table is written exclusively by this app, so an unknown name
 * is data corruption worth failing loudly on, not silently coercing.
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
)
