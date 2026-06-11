package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural coverage for [ResumePipelineRunUseCase] — every resume
 * precondition (status, prompt presence, age window, graph identity) and the
 * happy path that re-enqueues the run as a resume-flagged [AgentTask].
 */
class ResumePipelineRunUseCaseTest {

    private lateinit var pipelineRunRepository: PipelineRunRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var taskQueueManager: TaskQueueManager

    private lateinit var useCase: ResumePipelineRunUseCase

    private val graph = PipelineGraph(
        id = "pipe-1",
        name = "Pipeline",
        nodes = listOf(
            NodeModel("input_1", NodeType.INPUT, 0f, 0f),
            NodeModel("output_1", NodeType.OUTPUT, 0f, 0f),
        ),
        connections = listOf(ConnectionModel("c1", "input_1", "output_1")),
    )

    @Before
    fun setup() {
        pipelineRunRepository = mockk()
        pipelineRepository = mockk()
        settingsRepository = mockk()
        taskQueueManager = mockk(relaxed = true)
        useCase = ResumePipelineRunUseCase(
            pipelineRunRepository = pipelineRunRepository,
            pipelineRepository = pipelineRepository,
            settingsRepository = settingsRepository,
            taskQueueManager = taskQueueManager,
        )
        every { settingsRepository.resumeMaxAgeHours } returns flowOf(48)
        coEvery { pipelineRepository.getPipelineById("pipe-1") } returns graph
        coEvery { pipelineRunRepository.markResumed(any()) } returns true
    }

    /** Interrupted run record matching [graph] and fresh enough to resume. */
    private fun interruptedRun(
        status: PipelineRunStatus = PipelineRunStatus.INTERRUPTED,
        pipelineId: String? = "pipe-1",
        graphContentHash: String? = graph.contentHash(),
        userPrompt: String? = "do the thing",
        finishedAt: Long? = System.currentTimeMillis(),
    ): PipelineRun = PipelineRun(
        id = "run-1",
        sessionId = "session-1",
        pipelineId = pipelineId,
        origin = RunOrigin.CHAT,
        status = status,
        currentNodeId = "output_1",
        startedAt = 0L,
        finishedAt = finishedAt,
        errorMessage = "Interrupted",
        graphContentHash = graphContentHash,
        userPrompt = userPrompt,
    )

    @Test
    fun `given valid interrupted run when invoked then marks resumed and enqueues resume task`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun()

        val outcome = useCase("run-1")

        assertEquals(ResumeOutcome.Resumed, outcome)
        coVerify { pipelineRunRepository.markResumed("run-1") }
        val task = slot<AgentTask>()
        verify { taskQueueManager.enqueueTask(capture(task)) }
        assertEquals("run-1", task.captured.id)
        assertEquals("session-1", task.captured.sessionId)
        assertEquals("do the thing", task.captured.prompt)
        assertEquals("pipe-1", task.captured.pipelineId)
        assertTrue(task.captured.isResume)
    }

    @Test
    fun `given run is missing when invoked then NotResumable`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns null

        assertEquals(ResumeOutcome.NotResumable, useCase("run-1"))
        verify(exactly = 0) { taskQueueManager.enqueueTask(any()) }
    }

    @Test
    fun `given run is not INTERRUPTED when invoked then NotResumable`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns
            interruptedRun(status = PipelineRunStatus.FAILED)

        assertEquals(ResumeOutcome.NotResumable, useCase("run-1"))
    }

    @Test
    fun `given legacy run without user prompt when invoked then NotResumable`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun(userPrompt = null)

        assertEquals(ResumeOutcome.NotResumable, useCase("run-1"))
    }

    @Test
    fun `given interruption older than the window when invoked then Expired`() = runTest {
        every { settingsRepository.resumeMaxAgeHours } returns flowOf(1)
        val twoHoursAgo = System.currentTimeMillis() - 2 * 3_600_000L
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun(finishedAt = twoHoursAgo)

        assertEquals(ResumeOutcome.Expired, useCase("run-1"))
        verify(exactly = 0) { taskQueueManager.enqueueTask(any()) }
    }

    @Test
    fun `given pipeline deleted since interruption when invoked then GraphChanged`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun()
        coEvery { pipelineRepository.getPipelineById("pipe-1") } returns null

        assertEquals(ResumeOutcome.GraphChanged, useCase("run-1"))
    }

    @Test
    fun `given graph edited since interruption when invoked then GraphChanged`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns
            interruptedRun(graphContentHash = "stale-hash")

        assertEquals(ResumeOutcome.GraphChanged, useCase("run-1"))
        verify(exactly = 0) { taskQueueManager.enqueueTask(any()) }
    }

    @Test
    fun `given run without recorded hash when invoked then GraphChanged`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun(graphContentHash = null)

        assertEquals(ResumeOutcome.GraphChanged, useCase("run-1"))
    }

    @Test
    fun `given concurrent transition lost the markResumed race when invoked then NotResumable`() = runTest {
        coEvery { pipelineRunRepository.getRun("run-1") } returns interruptedRun()
        coEvery { pipelineRunRepository.markResumed("run-1") } returns false

        assertEquals(ResumeOutcome.NotResumable, useCase("run-1"))
        verify(exactly = 0) { taskQueueManager.enqueueTask(any()) }
    }
}
