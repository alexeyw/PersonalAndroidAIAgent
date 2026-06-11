package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.TraceStepDao
import app.knotwork.android.data.local.models.TraceStepEntity
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.RunTraceRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RunTraceRepositoryImpl] — the buffered write path of the
 * persistent run trace.
 *
 * Covers the three flush triggers (size, timer, forced), the no-loss
 * guarantee at terminal flush points, in-order batch contents, the
 * entity↔domain mapping of both record kinds, and the best-effort contract
 * (storage failures absorbed on write, degraded-to-empty and row-skipping
 * reads).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTraceRepositoryImplTest {

    private lateinit var traceStepDao: TraceStepDao
    private lateinit var repository: RunTraceRepositoryImpl

    @Before
    fun setup() {
        traceStepDao = mockk(relaxed = true)
        repository = RunTraceRepositoryImpl(traceStepDao)
    }

    private fun consoleEntry(seq: Long, message: String = "msg-$seq"): RunTraceRecord.ConsoleEntry =
        RunTraceRecord.ConsoleEntry(
            runId = RUN_ID,
            sessionId = SESSION_ID,
            seq = seq,
            timestamp = 1_000L + seq,
            type = ConsoleEventType.NodeExecution,
            message = message,
        )

    private fun nodeIo(seq: Long): RunTraceRecord.NodeIo = RunTraceRecord.NodeIo(
        runId = RUN_ID,
        sessionId = SESSION_ID,
        seq = seq,
        timestamp = 1_000L + seq,
        nodeId = "node-$seq",
        nodeType = "LITE_RT",
        inputText = "in-$seq",
        outputText = "out-$seq",
        durationMs = 42L,
        tokenCount = 7,
    )

    @Test
    fun `given records below size threshold when timer elapses then batch is flushed once`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(capture(batches)) } returns Unit

        repository.append(consoleEntry(seq = 0))
        repository.append(consoleEntry(seq = 1))
        runCurrent()
        assertTrue("No insert before the timer fires", batches.isEmpty())

        advanceTimeBy(501L)
        runCurrent()

        assertEquals(1, batches.size)
        assertEquals(listOf(0L, 1L), batches.single().map { it.seq })
    }

    @Test
    fun `given buffer reaches flush size then batch is inserted immediately without timer`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(capture(batches)) } returns Unit

        repeat(32) { repository.append(consoleEntry(seq = it.toLong())) }
        runCurrent()

        assertEquals(1, batches.size)
        assertEquals(32, batches.single().size)
        // The pending timer was cancelled with the size-triggered flush —
        // letting it elapse must not produce a second (empty) insert.
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, batches.size)
    }

    @Test
    fun `given forced flush at terminal point then no buffered record is lost`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(capture(batches)) } returns Unit

        repository.append(consoleEntry(seq = 0))
        repository.append(nodeIo(seq = 1))
        repository.append(consoleEntry(seq = 2))
        repository.flush()
        runCurrent()

        assertEquals(1, batches.size)
        assertEquals(listOf(0L, 1L, 2L), batches.single().map { it.seq })
        // The deferred timer died with the forced flush — no duplicate insert.
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, batches.size)
    }

    @Test
    fun `given flush with empty buffer then dao is never touched`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)

        repository.flush()
        runCurrent()

        coVerify(exactly = 0) { traceStepDao.insertTraceSteps(any()) }
    }

    @Test
    fun `given mixed record kinds when flushed then entities carry the right columns`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(capture(batches)) } returns Unit

        repository.append(nodeIo(seq = 0))
        repository.append(consoleEntry(seq = 1, message = "▶ LITE_RT"))
        repository.flush()
        runCurrent()

        val (node, console) = batches.single()
        assertEquals(TraceStepEntity.KIND_NODE_IO, node.recordKind)
        assertEquals(RUN_ID, node.runId)
        assertEquals("node-0", node.nodeId)
        assertEquals("in-0", node.inputText)
        assertEquals("out-0", node.outputText)
        assertEquals("LITE_RT", node.nodeName)
        assertEquals(42L, node.durationMs)
        assertEquals(7, node.tokenCount)
        assertEquals(TraceStepEntity.KIND_CONSOLE_EVENT, console.recordKind)
        assertEquals("NODE_EXECUTION", console.consoleEventType)
        assertEquals("▶ LITE_RT", console.outputText)
        assertEquals("", console.nodeName)
        assertEquals(null, console.tokenCount)
    }

    @Test
    fun `given poisoned run in mixed batch then healthy run's records are retried per run and survive`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val insertedBatches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(any()) } answers {
            val batch = firstArg<List<TraceStepEntity>>()
            // run-poisoned has no parent pipeline_runs row: any transaction
            // containing its records fails the FK check as a whole.
            if (batch.any { it.runId == "run-poisoned" }) {
                throw RuntimeException("FOREIGN KEY constraint failed")
            }
            insertedBatches += batch
        }

        repository.append(consoleEntry(seq = 0))
        repository.append(consoleEntry(seq = 1).copy(runId = "run-poisoned"))
        repository.append(consoleEntry(seq = 2))
        repository.flush()
        runCurrent()

        // The mixed batch failed, the per-run retry salvaged the healthy
        // run's records; only the poisoned run's group was dropped.
        assertEquals(1, insertedBatches.size)
        assertEquals(listOf(0L, 2L), insertedBatches.single().map { it.seq })
        assertTrue(insertedBatches.single().all { it.runId == RUN_ID })
    }

    @Test
    fun `given storage failure on flush then batch is dropped and later appends still work`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<TraceStepEntity>>()
        coEvery { traceStepDao.insertTraceSteps(any()) } throws RuntimeException("disk full")

        repository.append(consoleEntry(seq = 0))
        repository.flush()
        runCurrent()

        coEvery { traceStepDao.insertTraceSteps(capture(batches)) } returns Unit
        repository.append(consoleEntry(seq = 1))
        repository.flush()
        runCurrent()

        assertEquals(1, batches.size)
        assertEquals(listOf(1L), batches.single().map { it.seq })
    }

    @Test
    fun `given persisted rows when getTraceForRun then records are mapped back in seq order`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { traceStepDao.getTraceStepsForRun(RUN_ID) } returns listOf(
            TraceStepEntity(
                id = 1, sessionId = SESSION_ID, nodeName = "LITE_RT", outputText = "out",
                timestamp = 10L, durationMs = 5L, tokenCount = 3, runId = RUN_ID, seq = 0,
                recordKind = TraceStepEntity.KIND_NODE_IO, nodeId = "n1", inputText = "in",
            ),
            TraceStepEntity(
                id = 2, sessionId = SESSION_ID, nodeName = "", outputText = "✓ LITE_RT in 5ms",
                timestamp = 11L, runId = RUN_ID, seq = 1,
                recordKind = TraceStepEntity.KIND_CONSOLE_EVENT, consoleEventType = "NODE_EXECUTION",
            ),
        )

        val records = repository.getTraceForRun(RUN_ID)

        assertEquals(2, records.size)
        val node = records[0] as RunTraceRecord.NodeIo
        assertEquals("n1", node.nodeId)
        assertEquals("in", node.inputText)
        assertEquals("out", node.outputText)
        val console = records[1] as RunTraceRecord.ConsoleEntry
        assertEquals(ConsoleEventType.NodeExecution, console.type)
        assertEquals("✓ LITE_RT in 5ms", console.message)
    }

    @Test
    fun `given unreadable rows when getTraceForRun then they are skipped not fatal`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { traceStepDao.getTraceStepsForRun(RUN_ID) } returns listOf(
            // Legacy row without run attribution.
            TraceStepEntity(id = 1, sessionId = SESSION_ID, nodeName = "LITE_RT", outputText = "x", timestamp = 1L),
            // Unknown discriminator (future schema version).
            TraceStepEntity(
                id = 2,
                sessionId = SESSION_ID,
                nodeName = "",
                outputText = "y",
                timestamp = 2L,
                runId = RUN_ID,
                seq = 0,
                recordKind = "FUTURE_KIND",
            ),
            // Unknown console event type.
            TraceStepEntity(
                id = 3, sessionId = SESSION_ID, nodeName = "", outputText = "z",
                timestamp = 3L, runId = RUN_ID, seq = 1,
                recordKind = TraceStepEntity.KIND_CONSOLE_EVENT, consoleEventType = "NOT_A_TYPE",
            ),
            // Healthy row survives.
            TraceStepEntity(
                id = 4, sessionId = SESSION_ID, nodeName = "", outputText = "ok",
                timestamp = 4L, runId = RUN_ID, seq = 2,
                recordKind = TraceStepEntity.KIND_CONSOLE_EVENT, consoleEventType = "ERROR",
            ),
        )

        val records = repository.getTraceForRun(RUN_ID)

        assertEquals(1, records.size)
        assertEquals("ok", (records.single() as RunTraceRecord.ConsoleEntry).message)
    }

    @Test
    fun `given storage failure when getTraceForRun then read degrades to empty`() = runTest {
        repository.dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { traceStepDao.getTraceStepsForRun(RUN_ID) } throws RuntimeException("corrupt")

        assertEquals(emptyList<RunTraceRecord>(), repository.getTraceForRun(RUN_ID))
    }

    private companion object {
        const val RUN_ID = "run-1"
        const val SESSION_ID = "session-1"
    }
}
