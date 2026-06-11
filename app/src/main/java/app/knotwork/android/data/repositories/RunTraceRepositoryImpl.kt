package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.local.dao.TraceStepDao
import app.knotwork.android.data.local.models.TraceStepEntity
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.repositories.RunTraceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Room-backed, write-buffered implementation of [RunTraceRepository].
 *
 * **Batching.** [append] only adds the record to an in-memory buffer guarded
 * by a [Mutex]; the buffer reaches storage as a single batch insert when one
 * of three triggers fires:
 *
 * 1. **Size** — the buffer holds [FLUSH_SIZE] records (a streaming LLM node
 *    emits console events far faster than SQLCipher should be asked to
 *    commit individual rows);
 * 2. **Timer** — [FLUSH_INTERVAL_MS] elapsed since the first record entered
 *    the empty buffer (bounds trace staleness during quiet phases);
 * 3. **Force** — the engine calls [flush] at suspension and terminal points,
 *    making the persisted trace complete at any moment the run can pause,
 *    end, or the process can be killed right after.
 *
 * Draining and inserting happen under the buffer mutex, so concurrent
 * flush triggers cannot reorder records and `seq` order is preserved in
 * insertion order.
 *
 * **Best-effort contract.** A failed batch insert is logged and the batch is
 * dropped — re-buffering a poisoned batch forever would grow the buffer
 * unbounded while the store stays broken. Reads degrade to an empty list;
 * a single unreadable row is skipped rather than discarding the whole trace.
 * [CancellationException] always propagates.
 */
@Singleton
class RunTraceRepositoryImpl @Inject constructor(private val traceStepDao: TraceStepDao) : RunTraceRepository {

    /**
     * Dispatcher carrying both DAO calls and the flush timer. Swapped in unit
     * tests; the setter rebuilds [timerScope] so a pending timer never
     * outlives the dispatcher it was scheduled on.
     */
    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO
        set(value) {
            field = value
            timerScope.cancel()
            timerScope = CoroutineScope(value + SupervisorJob())
        }

    /** Process-lifetime scope hosting the deferred flush timer. */
    private var timerScope = CoroutineScope(dispatcher + SupervisorJob())

    /** Guards [buffer] and [timerJob]; also serializes the drain-and-insert. */
    private val bufferMutex = Mutex()

    /** Records accepted by [append] and not yet written to storage. */
    private val buffer = ArrayDeque<TraceStepEntity>()

    /** Pending deferred-flush timer, `null` when the buffer is empty. */
    private var timerJob: Job? = null

    override suspend fun append(record: RunTraceRecord) {
        bufferMutex.withLock {
            buffer.addLast(record.toEntity())
            when {
                buffer.size >= FLUSH_SIZE -> drainAndInsertLocked()
                timerJob == null -> timerJob = timerScope.launch {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            }
        }
    }

    override suspend fun flush() {
        bufferMutex.withLock { drainAndInsertLocked() }
    }

    override suspend fun getTraceForRun(runId: String): List<RunTraceRecord> = try {
        withContext(dispatcher) {
            traceStepDao.getTraceStepsForRun(runId).mapNotNull { it.toRecordOrNull() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Run-trace store failure in getTraceForRun; degrading to empty")
        emptyList()
    }

    /**
     * Drains the buffer and batch-inserts the drained records. Must be called
     * under [bufferMutex]. Cancels the pending timer — the work it was
     * scheduled for is being done right now — unless this call *is* the timer
     * firing: cancelling the calling coroutine would abort the insert below
     * and silently drop the drained batch. A storage failure drops the
     * drained batch (logged) per the best-effort contract.
     */
    private suspend fun drainAndInsertLocked() {
        val pendingTimer = timerJob
        timerJob = null
        if (pendingTimer != null && pendingTimer != coroutineContext[Job]) {
            pendingTimer.cancel()
        }
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        try {
            withContext(dispatcher) {
                traceStepDao.insertTraceSteps(batch)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Run-trace store failure flushing %d records; batch dropped", batch.size)
        }
    }

    private companion object {
        /** Buffer size that triggers an immediate flush. */
        const val FLUSH_SIZE: Int = 32

        /** Max time a record may sit in the buffer before a deferred flush. */
        const val FLUSH_INTERVAL_MS: Long = 500L
    }
}

/**
 * Maps the domain trace record to its persistence row. Console entries store
 * their message in the shared `outputText` payload column and an empty
 * `nodeName` (they are not tied to a node) — see [TraceStepEntity].
 *
 * @return The entity ready for insertion.
 */
private fun RunTraceRecord.toEntity(): TraceStepEntity = when (this) {
    is RunTraceRecord.NodeIo -> TraceStepEntity(
        sessionId = sessionId,
        nodeName = nodeType,
        outputText = outputText,
        timestamp = timestamp,
        durationMs = durationMs,
        tokenCount = tokenCount,
        runId = runId,
        seq = seq,
        recordKind = TraceStepEntity.KIND_NODE_IO,
        nodeId = nodeId,
        inputText = inputText,
    )
    is RunTraceRecord.ConsoleEntry -> TraceStepEntity(
        sessionId = sessionId,
        nodeName = "",
        outputText = message,
        timestamp = timestamp,
        runId = runId,
        seq = seq,
        recordKind = TraceStepEntity.KIND_CONSOLE_EVENT,
        consoleEventType = type.toStorageName(),
    )
}

/**
 * Maps a persistence row back to the domain record, or `null` when the row
 * cannot be interpreted (no run attribution, unknown kind or console type) —
 * the read path skips such rows instead of failing the whole trace.
 *
 * @return The domain record, or `null` for an unreadable row.
 */
private fun TraceStepEntity.toRecordOrNull(): RunTraceRecord? {
    val recordRunId = runId ?: return null
    return when (recordKind) {
        TraceStepEntity.KIND_NODE_IO -> RunTraceRecord.NodeIo(
            runId = recordRunId,
            sessionId = sessionId,
            seq = seq,
            timestamp = timestamp,
            nodeId = nodeId.orEmpty(),
            nodeType = nodeName,
            inputText = inputText.orEmpty(),
            outputText = outputText,
            durationMs = durationMs,
            tokenCount = tokenCount,
        )
        TraceStepEntity.KIND_CONSOLE_EVENT -> consoleEventTypeFromStorage(consoleEventType)?.let { type ->
            RunTraceRecord.ConsoleEntry(
                runId = recordRunId,
                sessionId = sessionId,
                seq = seq,
                timestamp = timestamp,
                type = type,
                message = outputText,
            )
        }
        else -> {
            Timber.w("Skipping trace row %d with unknown recordKind '%s'", id, recordKind)
            null
        }
    }
}

/**
 * Stable storage name of a [ConsoleEventType] variant. The sealed hierarchy
 * has no `name` property, so the mapping is explicit — renaming a variant
 * must not silently change what is on disk.
 *
 * @return The storage discriminator string.
 */
private fun ConsoleEventType.toStorageName(): String = when (this) {
    ConsoleEventType.NodeExecution -> "NODE_EXECUTION"
    ConsoleEventType.ToolCall -> "TOOL_CALL"
    ConsoleEventType.MemoryAccess -> "MEMORY_ACCESS"
    ConsoleEventType.SystemMessage -> "SYSTEM_MESSAGE"
    ConsoleEventType.Error -> "ERROR"
}

/**
 * Inverse of [toStorageName].
 *
 * @param name The stored discriminator, possibly `null` or unknown.
 * @return The matching [ConsoleEventType], or `null` when unrecognized.
 */
private fun consoleEventTypeFromStorage(name: String?): ConsoleEventType? = when (name) {
    "NODE_EXECUTION" -> ConsoleEventType.NodeExecution
    "TOOL_CALL" -> ConsoleEventType.ToolCall
    "MEMORY_ACCESS" -> ConsoleEventType.MemoryAccess
    "SYSTEM_MESSAGE" -> ConsoleEventType.SystemMessage
    "ERROR" -> ConsoleEventType.Error
    else -> {
        Timber.w("Unknown stored console event type '%s'; skipping row", name)
        null
    }
}
