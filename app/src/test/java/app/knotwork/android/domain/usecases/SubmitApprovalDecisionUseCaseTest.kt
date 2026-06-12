package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SubmitApprovalDecisionUseCase] — the single entry point of
 * the user's approve / deny decision across both waiting phases.
 *
 * Cover: live-gate routing, the parked-record fallthrough (by run id and by
 * session), the decision recording handed to [ParkedRunResumer], kind
 * filtering, and the no-pending case.
 */
class SubmitApprovalDecisionUseCaseTest {

    private lateinit var taskQueueManager: TaskQueueManager
    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var parkedRunResumer: ParkedRunResumer
    private lateinit var useCase: SubmitApprovalDecisionUseCase

    @Before
    fun setup() {
        taskQueueManager = mockk(relaxed = true)
        pendingInteractionRepository = mockk(relaxed = true)
        parkedRunResumer = mockk()
        useCase = SubmitApprovalDecisionUseCase(
            taskQueueManager = taskQueueManager,
            pendingInteractionRepository = pendingInteractionRepository,
            parkedRunResumer = parkedRunResumer,
        )
        every { taskQueueManager.pendingApproval(any()) } returns null
        coEvery { pendingInteractionRepository.getForRun(any()) } returns null
        coEvery { pendingInteractionRepository.getForSession(any()) } returns null
        coEvery { parkedRunResumer.submit(any(), any()) } returns PendingSubmissionOutcome.Resumed
    }

    /** A parked approval record for the fallthrough tests. */
    private fun parkedApproval(): PendingInteraction = PendingInteraction(
        runId = "run-1",
        sessionId = "session-1",
        kind = PendingInteractionKind.APPROVAL,
        toolName = "tool",
        toolArgs = "{}",
        risk = ToolRisk.SENSITIVE,
        requestedAt = 0L,
    )

    @Test
    fun `given live gate when invoked then resumes in place and never touches the store`() = runTest {
        every { taskQueueManager.pendingApproval("session-1") } returns
            AgentOrchestratorState.WaitingForApproval("tool", "{}", ToolRisk.SENSITIVE)

        val outcome = useCase("session-1", isApproved = true)

        assertEquals(PendingSubmissionOutcome.LiveResumed, outcome)
        verify { taskQueueManager.resumeWithApproval("session-1", true) }
        coVerify(exactly = 0) { pendingInteractionRepository.getForRun(any()) }
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
    }

    @Test
    fun `given parked record addressed by run id when invoked then submits through the resumer`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedApproval()

        val outcome = useCase("session-1", isApproved = true, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify { parkedRunResumer.submit(match { it.runId == "run-1" }, any()) }
    }

    @Test
    fun `given no run id when invoked then falls back to the session's parked record`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedApproval()

        val outcome = useCase("session-1", isApproved = false)

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify { parkedRunResumer.submit(match { it.runId == "run-1" }, any()) }
    }

    @Test
    fun `given approval decision when submitted then APPROVED is recorded onto the record`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedApproval()
        val recorder = slot<suspend (String) -> Boolean>()
        coEvery { parkedRunResumer.submit(any(), capture(recorder)) } returns PendingSubmissionOutcome.Resumed
        coEvery { pendingInteractionRepository.recordDecision("run-1", PendingDecision.APPROVED) } returns true

        useCase("session-1", isApproved = true, runId = "run-1")

        assertTrue(recorder.captured("run-1"))
        coVerify { pendingInteractionRepository.recordDecision("run-1", PendingDecision.APPROVED) }
    }

    @Test
    fun `given denial when submitted then DENIED is recorded onto the record`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedApproval()
        val recorder = slot<suspend (String) -> Boolean>()
        coEvery { parkedRunResumer.submit(any(), capture(recorder)) } returns PendingSubmissionOutcome.Resumed
        coEvery { pendingInteractionRepository.recordDecision("run-1", PendingDecision.DENIED) } returns true

        useCase("session-1", isApproved = false, runId = "run-1")

        assertTrue(recorder.captured("run-1"))
        coVerify { pendingInteractionRepository.recordDecision("run-1", PendingDecision.DENIED) }
    }

    @Test
    fun `given only a clarification park when invoked then NothingPending`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedApproval().copy(
            kind = PendingInteractionKind.CLARIFICATION,
        )

        val outcome = useCase("session-1", isApproved = true)

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
    }

    @Test
    fun `given nothing pending anywhere when invoked then NothingPending`() = runTest {
        val outcome = useCase("session-1", isApproved = true)

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        verify(exactly = 0) { taskQueueManager.resumeWithApproval(any(), any()) }
    }
}
