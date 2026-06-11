package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.data.local.models.TraceStepEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [TraceStepDao] — the batch-only write surface of
 * the persistent run trace. Verifies the single-transaction batch insert,
 * the per-run `seq ASC` query contract (ordering, run scoping, column
 * round-trip), and both foreign-key cascades: deleting the owning
 * `chat_sessions` row removes the session's trace, deleting a
 * `pipeline_runs` row removes only that run's records while legacy rows
 * (no run attribution) survive.
 */
@RunWith(AndroidJUnit4::class)
class TraceStepDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var traceStepDao: TraceStepDao
    private lateinit var chatDao: ChatDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        traceStepDao = database.traceStepDao()
        chatDao = database.chatDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertTraceSteps_batchInsertsAllRows() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        insertRun(runId = "r1", sessionId = "s")

        traceStepDao.insertTraceSteps(
            (0L until 3L).map { seq ->
                consoleStep(runId = "r1", sessionId = "s", seq = seq)
            },
        )

        assertEquals(3, traceStepDao.getTraceStepsForRun("r1").size)
    }

    @Test
    fun getTraceStepsForRun_ordersBySeqAndFiltersByRun() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        insertRun(runId = "r1", sessionId = "s")
        insertRun(runId = "r2", sessionId = "s")

        traceStepDao.insertTraceSteps(
            listOf(
                consoleStep(runId = "r1", sessionId = "s", seq = 2L),
                consoleStep(runId = "r1", sessionId = "s", seq = 0L),
                consoleStep(runId = "r2", sessionId = "s", seq = 0L),
                consoleStep(runId = "r1", sessionId = "s", seq = 1L),
            ),
        )

        assertEquals(listOf(0L, 1L, 2L), traceStepDao.getTraceStepsForRun("r1").map { it.seq })
        assertEquals(listOf(0L), traceStepDao.getTraceStepsForRun("r2").map { it.seq })
    }

    @Test
    fun insertTraceSteps_roundTripsNodeIoColumns() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        insertRun(runId = "r1", sessionId = "s")

        traceStepDao.insertTraceSteps(
            listOf(
                TraceStepEntity(
                    sessionId = "s",
                    nodeName = "LITE_RT",
                    outputText = "hello",
                    timestamp = 100L,
                    durationMs = 250L,
                    tokenCount = 42,
                    runId = "r1",
                    seq = 0L,
                    recordKind = TraceStepEntity.KIND_NODE_IO,
                    nodeId = "llm_1",
                    inputText = "prompt",
                ),
                TraceStepEntity(
                    sessionId = "s",
                    nodeName = "TOOL",
                    outputText = "no-tokens",
                    timestamp = 200L,
                    durationMs = 12L,
                    tokenCount = null,
                    runId = "r1",
                    seq = 1L,
                    recordKind = TraceStepEntity.KIND_NODE_IO,
                    nodeId = "tool_1",
                    inputText = "args",
                ),
            ),
        )

        val rows = traceStepDao.getTraceStepsForRun("r1")
        val litert = rows.first { it.nodeName == "LITE_RT" }
        val tool = rows.first { it.nodeName == "TOOL" }
        assertEquals(250L, litert.durationMs)
        assertEquals(42, litert.tokenCount)
        assertEquals("llm_1", litert.nodeId)
        assertEquals("prompt", litert.inputText)
        assertEquals(12L, tool.durationMs)
        assertNull(tool.tokenCount)
    }

    @Test
    fun deletingOwningSession_cascadesTraceSteps() = runBlocking {
        // Room enables FK enforcement by default, so deleting the owning
        // chat_sessions row must cascade to trace_steps via the
        // `ON DELETE CASCADE` declared on the entity — this is the only
        // session-scoped cleanup path now that the per-session delete query
        // is gone.
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        insertRun(runId = "r1", sessionId = "s")
        traceStepDao.insertTraceSteps(listOf(consoleStep(runId = "r1", sessionId = "s", seq = 0L)))

        chatDao.deleteSession("s")

        assertTrue(traceStepDao.getTraceStepsForRun("r1").isEmpty())
    }

    @Test
    fun deletingOwningRun_cascadesItsTraceRecordsOnly() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        insertRun(runId = "r1", sessionId = "s")
        traceStepDao.insertTraceSteps(listOf(consoleStep(runId = "r1", sessionId = "s", seq = 0L)))
        // A legacy row without run attribution must survive run deletion.
        traceStepDao.insertTraceSteps(
            listOf(
                TraceStepEntity(
                    sessionId = "s",
                    nodeName = "LEGACY",
                    outputText = "legacy output",
                    timestamp = 1L,
                ),
            ),
        )

        database.openHelper.writableDatabase.execSQL("DELETE FROM pipeline_runs WHERE id = 'r1'")

        assertTrue(traceStepDao.getTraceStepsForRun("r1").isEmpty())
        assertEquals(1, countTraceRowsForSession("s"))
    }

    /**
     * Counts every `trace_steps` row of a session through a raw cursor —
     * legacy rows carry no `runId`, so the per-run DAO query cannot see them.
     */
    private fun countTraceRowsForSession(sessionId: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM trace_steps WHERE sessionId = ?", arrayOf(sessionId))
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun consoleStep(runId: String, sessionId: String, seq: Long): TraceStepEntity = TraceStepEntity(
        sessionId = sessionId,
        nodeName = "",
        outputText = "▶ NODE",
        timestamp = seq,
        runId = runId,
        seq = seq,
        recordKind = TraceStepEntity.KIND_CONSOLE_EVENT,
        consoleEventType = "NODE_EXECUTION",
    )

    private suspend fun insertRun(runId: String, sessionId: String) {
        database.pipelineRunDao().insertRun(
            PipelineRunEntity(
                id = runId,
                sessionId = sessionId,
                pipelineId = "p1",
                origin = "CHAT",
                status = "COMPLETED",
                currentNodeId = null,
                startedAt = 0L,
                finishedAt = 1L,
                errorMessage = null,
                graphContentHash = null,
            ),
        )
    }
}
