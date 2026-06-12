package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SubmitClarificationAnswerUseCase] — the single entry point
 * of the user's clarification answer across both waiting phases.
 *
 * Cover: live-deferred routing, the parked-record fallthrough when the live
 * submission is not consumed, the answer recording handed to
 * [ParkedRunResumer], kind filtering, and the no-pending case.
 */
class SubmitClarificationAnswerUseCaseTest {

    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var parkedRunResumer: ParkedRunResumer
    private lateinit var useCase: SubmitClarificationAnswerUseCase

    @Before
    fun setup() {
        clarificationRepository = mockk()
        pendingInteractionRepository = mockk(relaxed = true)
        parkedRunResumer = mockk()
        useCase = SubmitClarificationAnswerUseCase(
            clarificationRepository = clarificationRepository,
            pendingInteractionRepository = pendingInteractionRepository,
            parkedRunResumer = parkedRunResumer,
        )
        coEvery { clarificationRepository.submitClarification(any(), any()) } returns false
        coEvery { pendingInteractionRepository.getForSession(any()) } returns null
        coEvery { parkedRunResumer.submit(any(), any()) } returns PendingSubmissionOutcome.Resumed
    }

    /** A parked clarification record for the fallthrough tests. */
    private fun parkedClarification(): PendingInteraction = PendingInteraction(
        runId = "run-1",
        sessionId = "session-1",
        kind = PendingInteractionKind.CLARIFICATION,
        question = "Which one?",
        options = listOf("a", "b"),
        requestedAt = 0L,
    )

    @Test
    fun `given live deferred consumes the answer when invoked then LiveResumed`() = runTest {
        coEvery { clarificationRepository.submitClarification("req-1", "a") } returns true

        val outcome = useCase("session-1", requestId = "req-1", answer = "a")

        assertEquals(PendingSubmissionOutcome.LiveResumed, outcome)
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
    }

    @Test
    fun `given stale live request and a parked record when invoked then submits through the resumer`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedClarification()

        val outcome = useCase("session-1", requestId = "req-stale", answer = "a")

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify { parkedRunResumer.submit(match { it.runId == "run-1" }, any()) }
    }

    @Test
    fun `given no live request id when invoked then goes straight to the parked record`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedClarification()

        val outcome = useCase("session-1", requestId = null, answer = "b")

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify(exactly = 0) { clarificationRepository.submitClarification(any(), any()) }
    }

    @Test
    fun `given answer submission when routed to the resumer then the answer is recorded`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedClarification()
        val recorder = slot<suspend (String) -> Boolean>()
        coEvery { parkedRunResumer.submit(any(), capture(recorder)) } returns PendingSubmissionOutcome.Resumed
        coEvery { pendingInteractionRepository.recordAnswer("run-1", "b") } returns true

        useCase("session-1", requestId = null, answer = "b")

        assertTrue(recorder.captured("run-1"))
        coVerify { pendingInteractionRepository.recordAnswer("run-1", "b") }
    }

    @Test
    fun `given only an approval park when invoked then NothingPending`() = runTest {
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedClarification().copy(
            kind = PendingInteractionKind.APPROVAL,
            toolName = "tool",
            toolArgs = "{}",
            risk = ToolRisk.SENSITIVE,
        )

        val outcome = useCase("session-1", requestId = null, answer = "a")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
    }

    @Test
    fun `given nothing pending anywhere when invoked then NothingPending`() = runTest {
        val outcome = useCase("session-1", requestId = null, answer = "a")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
    }
}
