package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for [ChargingTriggerSweepWorker] — the one-shot worker
 * [PowerConnectionReceiver] enqueues on a power edge to fire charging triggers
 * immediately. It must visit only charging triggers (network/interval triggers
 * stay on the poll path) and delegate every fire to [FireTriggerUseCase].
 */
@RunWith(RobolectricTestRunner::class)
class ChargingTriggerSweepWorkerTest {

    private lateinit var context: Context
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var fireTriggerUseCase: FireTriggerUseCase

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        triggerRepository = mockk()
        fireTriggerUseCase = mockk(relaxed = true)
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = ChargingTriggerSweepWorker(
            appContext,
            workerParameters,
            triggerRepository,
            fireTriggerUseCase,
        )
    }

    private fun buildWorker(): ChargingTriggerSweepWorker =
        TestListenableWorkerBuilder<ChargingTriggerSweepWorker>(context)
            .setWorkerFactory(workerFactory())
            .build()

    private fun trigger(id: String, condition: TriggerCondition): Trigger = Trigger(
        id = id,
        name = id,
        condition = condition,
        pipelineId = "pipe",
        prompt = "do it",
        enabled = true,
        createdAt = 0L,
    )

    @Test
    fun `given mixed active triggers when swept then fires only charging ones`() = runTest {
        every { triggerRepository.observeActiveTriggers() } returns flowOf(
            listOf(
                trigger("c1", TriggerCondition.Charging),
                trigger("c2", TriggerCondition.Charging),
                trigger("n1", TriggerCondition.NetworkConnected(wifiOnly = false)),
                trigger("i1", TriggerCondition.IntervalSchedule(30)),
            ),
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Default-arg nowMillis means the stub must match (id, any()).
        coVerify(exactly = 1) { fireTriggerUseCase("c1", any()) }
        coVerify(exactly = 1) { fireTriggerUseCase("c2", any()) }
        coVerify(exactly = 0) { fireTriggerUseCase("n1", any()) }
        coVerify(exactly = 0) { fireTriggerUseCase("i1", any()) }
    }

    @Test
    fun `given no charging triggers when swept then fires nothing and succeeds`() = runTest {
        every { triggerRepository.observeActiveTriggers() } returns flowOf(
            listOf(trigger("n1", TriggerCondition.NetworkConnected(wifiOnly = true))),
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { fireTriggerUseCase(any(), any()) }
    }

    @Test
    fun `given the active-trigger read throws when swept then retries`() = runTest {
        every { triggerRepository.observeActiveTriggers() } returns flow { throw IllegalStateException("db gone") }

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
