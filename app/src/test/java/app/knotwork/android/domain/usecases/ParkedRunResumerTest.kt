package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.services.ClarificationNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ParkedRunResumer] — the shared submission tail of the
 * background-HITL decision use cases.
 *
 * Cover: notification teardown by kind, the lazy approval-window expiry, the
 * first-writer-wins response gate, the resume-outcome mapping (including the
 * failure settlement of GraphChanged / Expired / NotResumable parks), and the
 * [ParkedRunResumer.failPark] settlement shared with the maintenance worker.
 */
class ParkedRunResumerTest {

    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var pipelineRunRepository: PipelineRunRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var approvalNotifier: ApprovalNotifier
    private lateinit var clarificationNotifier: ClarificationNotifier
    private lateinit var resumePipelineRunUseCase: ResumePipelineRunUseCase
    private lateinit var resumer: ParkedRunResumer

    @Before
    fun setup() {
        pendingInteractionRepository = mockk(relaxed = true)
        pipelineRunRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        approvalNotifier = mockk(relaxed = true)
        clarificationNotifier = mockk(relaxed = true)
        resumePipelineRunUseCase = mockk()
        resumer = ParkedRunResumer(
            pendingInteractionRepository = pendingInteractionRepository,
            pipelineRunRepository = pipelineRunRepository,
            settingsRepository = settingsRepository,
            approvalNotifier = approvalNotifier,
            clarificationNotifier = clarificationNotifier,
            resumePipelineRunUseCase = resumePipelineRunUseCase,
        )
        coEvery { settingsRepository.backgroundApprovalWindowHours } returns flowOf(24)
        coEvery { resumePipelineRunUseCase("run-1") } returns ResumeOutcome.Resumed
    }

    /** A parked approval still inside its window unless [requestedAt] says otherwise. */
    private fun parkedApproval(requestedAt: Long = System.currentTimeMillis()): PendingInteraction = PendingInteraction(
        runId = "run-1",
        sessionId = "session-1",
        kind = PendingInteractionKind.APPROVAL,
        toolName = "tool",
        toolArgs = "{}",
        risk = ToolRisk.SENSITIVE,
        requestedAt = requestedAt,
    )

    @Test
    fun `given fresh park and recorded response when submit then run resumes`() = runTest {
        val outcome = resumer.submit(parkedApproval()) { true }

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify { resumePipelineRunUseCase("run-1") }
        verify { approvalNotifier.cancelApprovalNotification("session-1") }
    }

    @Test
    fun `given clarification park when submit then clarification notification is cancelled`() = runTest {
        val pending = parkedApproval().copy(
            kind = PendingInteractionKind.CLARIFICATION,
            toolName = null,
            toolArgs = null,
            risk = null,
            question = "Q?",
        )

        resumer.submit(pending) { true }

        verify { clarificationNotifier.cancelClarificationNotification("session-1") }
        verify(exactly = 0) { approvalNotifier.cancelApprovalNotification(any()) }
    }

    @Test
    fun `given park older than the window when submit then run fails as expired without resuming`() = runTest {
        val expiredAt = System.currentTimeMillis() - 25 * 3_600_000L

        val outcome = resumer.submit(parkedApproval(requestedAt = expiredAt)) { true }

        assertEquals(PendingSubmissionOutcome.Expired, outcome)
        coVerify {
            pipelineRunRepository.finishRun(
                "run-1",
                PipelineRunStatus.FAILED,
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
            )
        }
        coVerify { pendingInteractionRepository.delete("run-1") }
        coVerify(exactly = 0) { resumePipelineRunUseCase(any()) }
    }

    @Test
    fun `given response already recorded by a racing writer when submit then NothingPending`() = runTest {
        val outcome = resumer.submit(parkedApproval()) { false }

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { resumePipelineRunUseCase(any()) }
    }

    @Test
    fun `given resume reports GraphChanged when submit then park is failed and cleaned`() = runTest {
        coEvery { resumePipelineRunUseCase("run-1") } returns ResumeOutcome.GraphChanged

        val outcome = resumer.submit(parkedApproval()) { true }

        assertEquals(PendingSubmissionOutcome.GraphChanged, outcome)
        coVerify {
            pipelineRunRepository.finishRun("run-1", PipelineRunStatus.FAILED, ParkedRunResumer.GRAPH_CHANGED_MESSAGE)
        }
        coVerify { pendingInteractionRepository.delete("run-1") }
    }

    @Test
    fun `given resume reports Expired when submit then park is failed as expired`() = runTest {
        coEvery { resumePipelineRunUseCase("run-1") } returns ResumeOutcome.Expired

        val outcome = resumer.submit(parkedApproval()) { true }

        assertEquals(PendingSubmissionOutcome.Expired, outcome)
        coVerify {
            pipelineRunRepository.finishRun(
                "run-1",
                PipelineRunStatus.FAILED,
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
            )
        }
    }

    @Test
    fun `given resume reports NotResumable when submit then stale record is dropped`() = runTest {
        coEvery { resumePipelineRunUseCase("run-1") } returns ResumeOutcome.NotResumable

        val outcome = resumer.submit(parkedApproval()) { true }

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify { pendingInteractionRepository.delete("run-1") }
        // The run record was settled elsewhere — no second terminal write.
        coVerify(exactly = 0) { pipelineRunRepository.finishRun(any(), any(), any()) }
    }

    @Test
    fun `failPark fails the run deletes the record and removes the notification`() = runTest {
        resumer.failPark(parkedApproval(), "reason")

        coVerify { pipelineRunRepository.finishRun("run-1", PipelineRunStatus.FAILED, "reason") }
        coVerify { pendingInteractionRepository.delete("run-1") }
        verify { approvalNotifier.cancelApprovalNotification("session-1") }
    }
}
