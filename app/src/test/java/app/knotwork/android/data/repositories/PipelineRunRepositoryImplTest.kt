package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.PipelineRunDao
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunOrigin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PipelineRunRepositoryImpl]: entity↔domain mapping, the
 * terminal-guard plumbing (every mutating call must pass the terminal status
 * list to the DAO), the ownership-filtered orphan query, and the best-effort
 * contract (storage failures are absorbed, never propagated).
 */
class PipelineRunRepositoryImplTest {

    private lateinit var pipelineRunDao: PipelineRunDao
    private lateinit var repository: PipelineRunRepositoryImpl

    private val terminalNames = listOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED")
    private val activeNames = listOf("QUEUED", "RUNNING", "WAITING_APPROVAL", "WAITING_CLARIFICATION")

    private val sampleRun = PipelineRun(
        id = "run-1",
        sessionId = "session-1",
        pipelineId = null,
        origin = RunOrigin.CHAT,
        status = PipelineRunStatus.QUEUED,
        currentNodeId = null,
        startedAt = 1_000L,
        finishedAt = null,
        errorMessage = null,
        graphContentHash = null,
    )

    private val sampleEntity = PipelineRunEntity(
        id = "run-1",
        sessionId = "session-1",
        pipelineId = "pipe-1",
        origin = "SCHEDULER",
        status = "WAITING_APPROVAL",
        currentNodeId = "node-7",
        startedAt = 1_000L,
        finishedAt = null,
        errorMessage = null,
        graphContentHash = "abc",
    )

    @Before
    fun setup() {
        pipelineRunDao = mockk(relaxed = true)
        repository = PipelineRunRepositoryImpl(pipelineRunDao)
    }

    @Test
    fun `given queued run when createRun then entity stores enum names`() = runTest {
        val captured = slot<PipelineRunEntity>()
        coEvery { pipelineRunDao.insertRun(capture(captured)) } returns Unit

        repository.createRun(sampleRun)

        assertEquals("run-1", captured.captured.id)
        assertEquals("CHAT", captured.captured.origin)
        assertEquals("QUEUED", captured.captured.status)
        assertNull(captured.captured.pipelineId)
        assertNull(captured.captured.graphContentHash)
    }

    @Test
    fun `given run when markRunning then DAO receives RUNNING and terminal guard`() = runTest {
        repository.markRunning("run-1", "pipe-1", "hash-1")

        coVerify {
            pipelineRunDao.markRunning(
                runId = "run-1",
                status = "RUNNING",
                pipelineId = "pipe-1",
                graphContentHash = "hash-1",
                terminalStatuses = terminalNames,
            )
        }
    }

    @Test
    fun `given run when updateStatus then DAO receives status name and terminal guard`() = runTest {
        repository.updateStatus("run-1", PipelineRunStatus.WAITING_CLARIFICATION)

        coVerify {
            pipelineRunDao.updateStatus(
                runId = "run-1",
                status = "WAITING_CLARIFICATION",
                terminalStatuses = terminalNames,
            )
        }
    }

    @Test
    fun `given run when updateCurrentNode then DAO receives node id and terminal guard`() = runTest {
        repository.updateCurrentNode("run-1", "node-3")

        coVerify {
            pipelineRunDao.updateCurrentNode(
                runId = "run-1",
                nodeId = "node-3",
                terminalStatuses = terminalNames,
            )
        }
    }

    @Test
    fun `given terminal status when finishRun then DAO receives timestamp and message`() = runTest {
        val finishedAt = slot<Long>()

        repository.finishRun("run-1", PipelineRunStatus.INTERRUPTED, "process died")

        coVerify {
            pipelineRunDao.finishRun(
                runId = "run-1",
                status = "INTERRUPTED",
                finishedAt = capture(finishedAt),
                errorMessage = "process died",
                terminalStatuses = terminalNames,
            )
        }
        assertTrue(finishedAt.captured > 0L)
    }

    @Test
    fun `given non-terminal status when finishRun then throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.finishRun("run-1", PipelineRunStatus.RUNNING)
            }
        }
        coVerify(exactly = 0) { pipelineRunDao.finishRun(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given entity when getActiveRunForSession then maps to domain with parsed enums`() = runTest {
        val statuses = slot<List<String>>()
        coEvery {
            pipelineRunDao.getActiveRunForSession("session-1", capture(statuses))
        } returns sampleEntity

        val run = repository.getActiveRunForSession("session-1")

        assertEquals(RunOrigin.SCHEDULER, run?.origin)
        assertEquals(PipelineRunStatus.WAITING_APPROVAL, run?.status)
        assertEquals("node-7", run?.currentNodeId)
        // Active lookup must match exactly the non-terminal statuses.
        assertEquals(activeNames, statuses.captured)
    }

    @Test
    fun `given no active run when getActiveRunForSession then returns null`() = runTest {
        coEvery { pipelineRunDao.getActiveRunForSession("session-1", any()) } returns null

        assertNull(repository.getActiveRunForSession("session-1"))
    }

    @Test
    fun `orphan query covers every non-terminal status`() = runTest {
        val statuses = slot<List<String>>()
        coEvery { pipelineRunDao.getRunsByStatuses(capture(statuses)) } returns listOf(
            sampleEntity.copy(status = "WAITING_APPROVAL"),
        )

        val orphans = repository.getOrphanedRuns()

        assertEquals(activeNames, statuses.captured)
        assertEquals(1, orphans.size)
        assertEquals(PipelineRunStatus.WAITING_APPROVAL, orphans.first().status)
    }

    @Test
    fun `orphan query excludes runs created by the current process`() = runTest {
        // Register run-1 as owned by this process; run-2 belongs to a dead one.
        repository.createRun(sampleRun)
        coEvery { pipelineRunDao.getRunsByStatuses(any()) } returns listOf(
            sampleEntity.copy(id = "run-1", status = "RUNNING", origin = "CHAT"),
            sampleEntity.copy(id = "run-2", status = "RUNNING", origin = "CHAT"),
        )

        val orphans = repository.getOrphanedRuns()

        assertEquals(listOf("run-2"), orphans.map { it.id })
    }

    @Test
    fun `given runs flow when observeRunsForSession then maps every entity`() = runTest {
        coEvery { pipelineRunDao.observeRunsForSession("session-1") } returns flowOf(
            listOf(sampleEntity, sampleEntity.copy(id = "run-2", status = "COMPLETED")),
        )

        val runs = repository.observeRunsForSession("session-1").first()

        assertEquals(2, runs.size)
        assertEquals(PipelineRunStatus.COMPLETED, runs[1].status)
    }

    // region Best-effort contract — storage failures are absorbed

    @Test
    fun `given DAO insert failure when createRun then absorbed and id still owned`() = runTest {
        coEvery { pipelineRunDao.insertRun(any()) } throws IllegalStateException("disk full")

        repository.createRun(sampleRun) // must not throw

        // Ownership registration must precede the failed insert: the orphan
        // sweep may never claim a run whose machinery lives in this process.
        coEvery { pipelineRunDao.getRunsByStatuses(any()) } returns listOf(
            sampleEntity.copy(id = sampleRun.id, status = "QUEUED", origin = "CHAT"),
        )
        assertEquals(emptyList<PipelineRun>(), repository.getOrphanedRuns())
    }

    @Test
    fun `given DAO failure when writes then absorbed`() = runTest {
        coEvery { pipelineRunDao.markRunning(any(), any(), any(), any(), any()) } throws
            IllegalStateException("disk full")
        coEvery { pipelineRunDao.updateStatus(any(), any(), any()) } throws IllegalStateException("disk full")
        coEvery { pipelineRunDao.updateCurrentNode(any(), any(), any()) } throws IllegalStateException("disk full")
        coEvery { pipelineRunDao.finishRun(any(), any(), any(), any(), any()) } throws
            IllegalStateException("disk full")

        // None of these may throw — run records are observability only.
        repository.markRunning("run-1", "pipe-1", "hash-1")
        repository.updateStatus("run-1", PipelineRunStatus.RUNNING)
        repository.updateCurrentNode("run-1", "node-3")
        repository.finishRun("run-1", PipelineRunStatus.COMPLETED)
    }

    @Test
    fun `given DAO failure when reads then degrade to neutral results`() = runTest {
        coEvery { pipelineRunDao.getActiveRunForSession(any(), any()) } throws IllegalStateException("io")
        coEvery { pipelineRunDao.getRunsByStatuses(any()) } throws IllegalStateException("io")

        assertNull(repository.getActiveRunForSession("session-1"))
        assertEquals(emptyList<PipelineRun>(), repository.getOrphanedRuns())
    }

    @Test
    fun `given corrupt status string when reading then degrades to null instead of crashing`() = runTest {
        coEvery { pipelineRunDao.getActiveRunForSession("session-1", any()) } returns
            sampleEntity.copy(status = "NOT_A_STATUS")

        assertNull(repository.getActiveRunForSession("session-1"))
    }

    @Test
    fun `given failing upstream when observeRunsForSession then emits empty list`() = runTest {
        coEvery { pipelineRunDao.observeRunsForSession("session-1") } returns flow {
            throw IllegalStateException("io")
        }

        assertEquals(emptyList<PipelineRun>(), repository.observeRunsForSession("session-1").first())
    }

    // endregion

    @Test
    fun `terminal flag covers exactly the four terminal statuses`() {
        val terminal = PipelineRunStatus.entries.filter { it.isTerminal }
        assertEquals(
            listOf(
                PipelineRunStatus.COMPLETED,
                PipelineRunStatus.FAILED,
                PipelineRunStatus.CANCELLED,
                PipelineRunStatus.INTERRUPTED,
            ),
            terminal,
        )
        assertTrue(PipelineRunStatus.entries.filterNot { it.isTerminal }.none { it.isTerminal })
    }
}
