package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.data.local.models.TraceStepEntity
import app.knotwork.android.domain.models.PipelineRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the retention queries of [PipelineRunDao] —
 * the per-session window delete and the max-age delete. Both run against
 * real SQLite because the per-session query relies on a correlated subquery
 * whose semantics (outer-row `sessionId` reference inside an aliased inner
 * scan) a mocked DAO cannot exercise. Also verifies the `trace_steps`
 * foreign-key cascade that makes a run delete remove its persisted trace.
 */
@RunWith(AndroidJUnit4::class)
class PipelineRunDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var pipelineRunDao: PipelineRunDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pipelineRunDao = database.pipelineRunDao()
        runBlocking {
            database.chatDao().insertSession(ChatSessionEntity(id = "s1", name = "n", updatedAt = 0L))
            database.chatDao().insertSession(ChatSessionEntity(id = "s2", name = "n", updatedAt = 0L))
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun deleteTerminalRunsBeyondSessionLimit_keepsMostRecentWindowPerSession() = runBlocking {
        // Five terminal runs in s1, started 0..4; window of 2 keeps the two
        // most recently started (3, 4) and deletes the three older ones.
        (0L..4L).forEach { insertRun(runId = "s1-r$it", sessionId = "s1", startedAt = it) }

        val deleted = pipelineRunDao.deleteTerminalRunsBeyondSessionLimit(
            keepPerSession = 2,
            terminalStatuses = TERMINAL,
        )

        assertEquals(3, deleted)
        assertNull(pipelineRunDao.getRun("s1-r0"))
        assertNull(pipelineRunDao.getRun("s1-r2"))
        assertNotNull(pipelineRunDao.getRun("s1-r3"))
        assertNotNull(pipelineRunDao.getRun("s1-r4"))
    }

    @Test
    fun deleteTerminalRunsBeyondSessionLimit_isScopedPerSession() = runBlocking {
        // Two runs per session with a window of 2: nothing qualifies even
        // though four runs exist in total — the window is per session.
        insertRun(runId = "s1-r0", sessionId = "s1", startedAt = 0L)
        insertRun(runId = "s1-r1", sessionId = "s1", startedAt = 1L)
        insertRun(runId = "s2-r0", sessionId = "s2", startedAt = 0L)
        insertRun(runId = "s2-r1", sessionId = "s2", startedAt = 1L)

        val deleted = pipelineRunDao.deleteTerminalRunsBeyondSessionLimit(
            keepPerSession = 2,
            terminalStatuses = TERMINAL,
        )

        assertEquals(0, deleted)
    }

    @Test
    fun deleteTerminalRunsBeyondSessionLimit_neverDeletesWaitingRuns() = runBlocking {
        // A parked WAITING_APPROVAL run older than the whole window must
        // survive: retention only ever deletes terminal statuses. It still
        // occupies a window slot (the window counts runs of any status), so
        // the oldest terminal run falls out of a window of 2.
        insertRun(runId = "waiting", sessionId = "s1", startedAt = 0L, status = "WAITING_APPROVAL", finishedAt = null)
        insertRun(runId = "old-done", sessionId = "s1", startedAt = 1L)
        insertRun(runId = "new-done-1", sessionId = "s1", startedAt = 2L)
        insertRun(runId = "new-done-2", sessionId = "s1", startedAt = 3L)

        val deleted = pipelineRunDao.deleteTerminalRunsBeyondSessionLimit(
            keepPerSession = 2,
            terminalStatuses = TERMINAL,
        )

        assertEquals(2, deleted)
        assertNotNull(pipelineRunDao.getRun("waiting"))
        assertNull(pipelineRunDao.getRun("old-done"))
        assertNull(pipelineRunDao.getRun("new-done-1"))
        assertNotNull(pipelineRunDao.getRun("new-done-2"))
    }

    @Test
    fun deleteTerminalRunsFinishedBefore_deletesOnlyOldTerminalRuns() = runBlocking {
        insertRun(runId = "ancient", sessionId = "s1", startedAt = 0L, finishedAt = 10L)
        insertRun(runId = "fresh", sessionId = "s1", startedAt = 1L, finishedAt = 200L)
        // Old but non-terminal: must survive the age sweep untouched.
        insertRun(
            runId = "waiting",
            sessionId = "s1",
            startedAt = 2L,
            status = "WAITING_CLARIFICATION",
            finishedAt = null,
        )

        val deleted = pipelineRunDao.deleteTerminalRunsFinishedBefore(cutoff = 100L, terminalStatuses = TERMINAL)

        assertEquals(1, deleted)
        assertNull(pipelineRunDao.getRun("ancient"))
        assertNotNull(pipelineRunDao.getRun("fresh"))
        assertNotNull(pipelineRunDao.getRun("waiting"))
    }

    @Test
    fun retentionDelete_cascadesPersistedTrace() = runBlocking {
        insertRun(runId = "doomed", sessionId = "s1", startedAt = 0L, finishedAt = 10L)
        database.traceStepDao().insertTraceSteps(
            listOf(
                TraceStepEntity(
                    sessionId = "s1",
                    nodeName = "",
                    outputText = "▶ NODE",
                    timestamp = 0L,
                    runId = "doomed",
                    seq = 0L,
                    recordKind = TraceStepEntity.KIND_CONSOLE_EVENT,
                    consoleEventType = "NODE_EXECUTION",
                ),
            ),
        )

        pipelineRunDao.deleteTerminalRunsFinishedBefore(cutoff = 100L, terminalStatuses = TERMINAL)

        assertTrue(database.traceStepDao().getTraceStepsForRun("doomed").isEmpty())
    }

    private suspend fun insertRun(
        runId: String,
        sessionId: String,
        startedAt: Long,
        status: String = "COMPLETED",
        finishedAt: Long? = startedAt + 1L,
    ) {
        pipelineRunDao.insertRun(
            PipelineRunEntity(
                id = runId,
                sessionId = sessionId,
                pipelineId = "p1",
                origin = "CHAT",
                status = status,
                currentNodeId = null,
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = null,
                graphContentHash = null,
            ),
        )
    }

    private companion object {
        /** Terminal status names, derived from the domain enum like the repository does. */
        val TERMINAL: List<String> = PipelineRunStatus.entries.filter { it.isTerminal }.map { it.name }
    }
}
