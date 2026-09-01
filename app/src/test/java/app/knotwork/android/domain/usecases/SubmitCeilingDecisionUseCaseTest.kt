package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SubmitCeilingDecisionUseCase].
 *
 * The cases are chosen around the two properties that make this gate different
 * from the two beside it: continuing **writes** something the run did not have
 * before (a granted portion, on the tree root, before the resume), and stopping
 * settles the run with the ceiling as its cause rather than as an abandoned
 * gate. Everything else is [ParkedRunResumer]'s, and is tested there.
 */
class SubmitCeilingDecisionUseCaseTest {

    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var pipelineRunRepository: PipelineRunRepository
    private lateinit var parkedRunResumer: ParkedRunResumer
    private lateinit var useCase: SubmitCeilingDecisionUseCase

    @Before
    fun setup() {
        pendingInteractionRepository = mockk(relaxed = true)
        pipelineRunRepository = mockk(relaxed = true)
        parkedRunResumer = mockk(relaxed = true)
        useCase = SubmitCeilingDecisionUseCase(
            pendingInteractionRepository = pendingInteractionRepository,
            pipelineRunRepository = pipelineRunRepository,
            parkedRunResumer = parkedRunResumer,
        )
        coEvery { pipelineRunRepository.getRootRunId(any()) } answers { firstArg() }
        coEvery { pendingInteractionRepository.recordDecision(any(), any()) } returns true
        // The resumer's tail is tested in its own suite; here it only has to run
        // the response-writing lambda and report what it did.
        coEvery { parkedRunResumer.submit(any(), any()) } coAnswers {
            val record = secondArg<suspend (String) -> Boolean>()
            if (record(firstArg<PendingInteraction>().runId)) {
                PendingSubmissionOutcome.Resumed
            } else {
                PendingSubmissionOutcome.NothingPending
            }
        }
    }

    private fun parkedCeiling(
        runId: String = "run-1",
        axis: RunCeilingAxis? = RunCeilingAxis.STEPS,
        limit: Int? = 15,
        spent: Int? = 15,
    ) = PendingInteraction(
        runId = runId,
        sessionId = "session-1",
        kind = PendingInteractionKind.CEILING,
        ceilingAxis = axis,
        ceilingLimit = limit,
        ceilingSpent = spent,
        requestedAt = System.currentTimeMillis(),
    )

    @Test
    fun `given continue then the axis that bound is granted one more portion and the run resumes`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedCeiling()

        val outcome = useCase("session-1", shouldContinue = true, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.Resumed, outcome)
        coVerify { pipelineRunRepository.extendCeiling("run-1", RunCeilingAxis.STEPS) }
    }

    @Test
    fun `given a pause raised inside a sub-pipeline then the grant lands on the tree root`() = runTest {
        // The park sits where the pause happened; the counters live with the
        // spend, on the record at the top of the tree. Granting the child would
        // raise a ceiling nothing reads and leave the root breaching.
        coEvery { pendingInteractionRepository.getForRun("child-run") } returns parkedCeiling(runId = "child-run")
        coEvery { pipelineRunRepository.getRootRunId("child-run") } returns "root-run"

        useCase("session-1", shouldContinue = true, runId = "child-run")

        coVerify { pipelineRunRepository.extendCeiling("root-run", RunCeilingAxis.STEPS) }
        coVerify(exactly = 0) { pipelineRunRepository.extendCeiling("child-run", any()) }
    }

    @Test
    fun `given a racing duplicate continue then only the winner buys a portion`() = runTest {
        // The record's `decision IS NULL` clause is the only mutual exclusion
        // available across processes — a notification tap and an in-chat tap can
        // genuinely land at once. Outside it, both taps would grant.
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedCeiling()
        coEvery { pendingInteractionRepository.recordDecision("run-1", PendingDecision.APPROVED) } returns false

        val outcome = useCase("session-1", shouldContinue = true, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { pipelineRunRepository.extendCeiling(any(), any()) }
    }

    @Test
    fun `given a record that cannot name its axis then nothing is written at all`() = runTest {
        // Writing the decision first and discovering the problem after would
        // leave a record that reads as already answered — permanently
        // unanswerable — behind a run that is still waiting.
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedCeiling(axis = null)

        val outcome = useCase("session-1", shouldContinue = true, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { pendingInteractionRepository.recordDecision(any(), any()) }
        coVerify(exactly = 0) { pipelineRunRepository.extendCeiling(any(), any()) }
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
    }

    @Test
    fun `given stop then the run is settled with the ceiling as its cause, not as an abandoned gate`() = runTest {
        // The typed cause is what keeps a trigger's health badge reading a
        // working safety limit as a limit; the DENIED resolution is what keeps
        // the journal from recording the user as absent when they answered.
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns parkedCeiling()
        val reason = slot<RunTerminationReason>()
        val resolution = slot<TriggerHitlResolution>()

        val outcome = useCase("session-1", shouldContinue = false, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify {
            parkedRunResumer.failPark(any(), any(), capture(reason), capture(resolution))
        }
        assertEquals(RunTerminationReason.StepCeiling(limit = 15, spent = 15), reason.captured)
        assertEquals(TriggerHitlResolution.DENIED, resolution.captured)
        coVerify(exactly = 0) { pipelineRunRepository.extendCeiling(any(), any()) }
    }

    @Test
    fun `given a token pause then stopping records the token ceiling, not the step one`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns
            parkedCeiling(axis = RunCeilingAxis.TOKENS, limit = 100_000, spent = 100_400)
        val reason = slot<RunTerminationReason>()

        useCase("session-1", shouldContinue = false, runId = "run-1")

        coVerify { parkedRunResumer.failPark(any(), any(), capture(reason), any()) }
        // The spend exceeds the limit, and that is not a bug: tokens are charged
        // a whole node's usage at once and only then compared. The record must
        // carry what actually happened.
        assertEquals(RunTerminationReason.TokenCeiling(limit = 100_000, spent = 100_400), reason.captured)
    }

    @Test
    fun `given no run id then the session's parked record is answered`() = runTest {
        // The in-chat path has no run id to hand: a pause raised inside a
        // sub-pipeline is recorded on the child, while the session's active run
        // is the parent.
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns parkedCeiling(runId = "child-run")
        coEvery { pipelineRunRepository.getRootRunId("child-run") } returns "root-run"

        useCase("session-1", shouldContinue = true, runId = null)

        coVerify { pipelineRunRepository.extendCeiling("root-run", RunCeilingAxis.STEPS) }
    }

    @Test
    fun `given a park of another kind then it is left alone`() = runTest {
        // A ceiling decision must never settle an approval waiting on the same
        // session: the two mean entirely different things by "yes".
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session-1",
            kind = PendingInteractionKind.APPROVAL,
            toolName = "search_tool",
            requestedAt = System.currentTimeMillis(),
        )

        val outcome = useCase("session-1", shouldContinue = true, runId = "run-1")

        assertEquals(PendingSubmissionOutcome.NothingPending, outcome)
        coVerify(exactly = 0) { parkedRunResumer.submit(any(), any()) }
        coVerify(exactly = 0) { parkedRunResumer.failPark(any(), any(), any(), any()) }
    }

    @Test
    fun `given nothing parked then the submission reports so instead of throwing`() = runTest {
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns null
        coEvery { pendingInteractionRepository.getForSession("session-1") } returns null

        assertEquals(
            PendingSubmissionOutcome.NothingPending,
            useCase("session-1", shouldContinue = true, runId = "run-1"),
        )
    }
}
