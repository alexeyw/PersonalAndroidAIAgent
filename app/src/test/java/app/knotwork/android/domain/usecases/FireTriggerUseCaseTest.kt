package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.NetworkStateRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PowerStateRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.TaskScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FireTriggerUseCase] — the worker-side orchestration that loads
 * a trigger, defers the fire/skip call to [EvaluateTriggerFiringUseCase], and
 * acts (enqueue via the scheduler, mark fired, or auto-disable on a deleted
 * pipeline). The evaluator is mocked so each act-branch is exercised in
 * isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FireTriggerUseCaseTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var powerStateRepository: PowerStateRepository
    private lateinit var networkStateRepository: NetworkStateRepository
    private lateinit var evaluate: EvaluateTriggerFiringUseCase
    private lateinit var taskScheduler: TaskScheduler
    private lateinit var useCase: FireTriggerUseCase

    private val triggerId = "t1"
    private val now = 1_000L
    private val trigger = Trigger(
        id = triggerId,
        name = "T",
        condition = TriggerCondition.Charging,
        pipelineId = "pipe-1",
        prompt = "do it",
        enabled = true,
        createdAt = 0L,
    )

    @Before
    fun setUp() {
        triggerRepository = mockk(relaxed = true)
        pipelineRepository = mockk()
        powerStateRepository = mockk { every { powerState } returns MutableStateFlow(PowerState()) }
        networkStateRepository = mockk { every { networkState } returns MutableStateFlow(NetworkState()) }
        evaluate = mockk()
        taskScheduler = mockk(relaxed = true)
        useCase = FireTriggerUseCase(
            triggerRepository = triggerRepository,
            pipelineRepository = pipelineRepository,
            powerStateRepository = powerStateRepository,
            networkStateRepository = networkStateRepository,
            evaluateTriggerFiring = evaluate,
            taskScheduler = taskScheduler,
        )
    }

    @Test
    fun `given missing trigger when invoked then NotFound and nothing scheduled`() = runTest {
        coEvery { triggerRepository.getTriggerById(triggerId) } returns null

        val outcome = useCase(triggerId, now)

        assertEquals(TriggerFireOutcome.NotFound, outcome)
        verify(exactly = 0) { taskScheduler.scheduleOneTime(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given skip decision when invoked then Skipped and nothing scheduled`() = runTest {
        coEvery { triggerRepository.getTriggerById(triggerId) } returns trigger
        every { evaluate(any(), any(), any(), any(), any()) } returns
            TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED)

        val outcome = useCase(triggerId, now)

        assertEquals(TriggerFireOutcome.Skipped(TriggerSkipReason.DEBOUNCED), outcome)
        verify(exactly = 0) { taskScheduler.scheduleOneTime(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { triggerRepository.markFired(any(), any()) }
    }

    @Test
    fun `given fire decision and existing pipeline when invoked then enqueues trigger run and marks fired`() = runTest {
        coEvery { triggerRepository.getTriggerById(triggerId) } returns trigger
        every { evaluate(any(), any(), any(), any(), any()) } returns TriggerFiringDecision.Fire("pipe-1", "do it")
        coEvery { pipelineRepository.getPipelineById("pipe-1") } returns mockk<PipelineGraph>()

        val outcome = useCase(triggerId, now)

        assertEquals(TriggerFireOutcome.Fired("pipe-1"), outcome)
        verify(exactly = 1) {
            taskScheduler.scheduleOneTime(
                prompt = "do it",
                delayMinutes = 0,
                sessionId = null,
                constraints = any(),
                pipelineId = "pipe-1",
                origin = RunOrigin.TRIGGER,
            )
        }
        coVerify(exactly = 1) { triggerRepository.markFired(triggerId, now) }
    }

    @Test
    fun `given fire decision but deleted pipeline when invoked then auto-disables and does not schedule`() = runTest {
        coEvery { triggerRepository.getTriggerById(triggerId) } returns trigger
        every { evaluate(any(), any(), any(), any(), any()) } returns TriggerFiringDecision.Fire("pipe-1", "do it")
        coEvery { pipelineRepository.getPipelineById("pipe-1") } returns null

        val outcome = useCase(triggerId, now)

        assertEquals(TriggerFireOutcome.Disabled("pipe-1"), outcome)
        coVerify(exactly = 1) { triggerRepository.setEnabled(triggerId, false) }
        verify(exactly = 0) { taskScheduler.scheduleOneTime(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { triggerRepository.markFired(any(), any()) }
    }
}
