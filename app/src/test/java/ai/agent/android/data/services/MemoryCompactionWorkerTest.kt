package ai.agent.android.data.services

import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.MemoryCompactionUseCase
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for the `@HiltWorker`-annotated [MemoryCompactionWorker].
 *
 * As with [AgentWorkerTest], Hilt's assisted-inject machinery only fires inside
 * a real application runtime, so the test hands the mocked collaborators to the
 * worker through a manual [WorkerFactory] and drives `doWork()` via
 * [TestListenableWorkerBuilder].
 */
@RunWith(RobolectricTestRunner::class)
class MemoryCompactionWorkerTest {

    private lateinit var context: Context
    private lateinit var useCase: MemoryCompactionUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var taskQueueManager: TaskQueueManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        useCase = mockk()
        settingsRepository = mockk()
        taskQueueManager = mockk()
        // Default: agent idle, so compaction is allowed to proceed.
        every { taskQueueManager.globalState } returns MutableStateFlow(AgentOrchestratorState.Idle)
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            MemoryCompactionWorker(appContext, workerParameters, useCase, settingsRepository, taskQueueManager)
    }

    private fun buildWorker(): MemoryCompactionWorker = TestListenableWorkerBuilder<MemoryCompactionWorker>(context)
        .setWorkerFactory(workerFactory())
        .build()

    @Test
    fun `given compaction disabled when doWork runs then returns success without compacting`() = runTest {
        every { settingsRepository.memoryCompactionEnabled } returns flowOf(false)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `given compaction enabled when doWork runs then delegates and returns success`() = runTest {
        every { settingsRepository.memoryCompactionEnabled } returns flowOf(true)
        coEvery { useCase.invoke(any()) } returns MemoryCompactionUseCase.MemoryCompactionOutcome(
            clustersProcessed = 2,
            chunksConsolidated = 7,
            chunksCreated = 2,
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { useCase.invoke(any()) }
    }

    @Test
    fun `given the agent is busy when doWork runs then defers without compacting`() = runTest {
        every { settingsRepository.memoryCompactionEnabled } returns flowOf(true)
        every { taskQueueManager.globalState } returns MutableStateFlow(AgentOrchestratorState.Loading)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `given the use case throws when doWork runs then returns retry`() = runTest {
        every { settingsRepository.memoryCompactionEnabled } returns flowOf(true)
        coEvery { useCase.invoke(any()) } throws RuntimeException("boom")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `unique work names match the public contract`() {
        assertEquals("memory-compaction-periodic", MemoryCompactionWorker.UNIQUE_PERIODIC_NAME)
        assertEquals("memory-compaction-immediate", MemoryCompactionWorker.UNIQUE_IMMEDIATE_NAME)
    }
}
