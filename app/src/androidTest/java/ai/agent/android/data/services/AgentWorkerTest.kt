package ai.agent.android.data.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.models.AgentOrchestratorState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentWorkerTest {

    private lateinit var context: Context
    private lateinit var useCase: AgentOrchestratorUseCase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        useCase = mockk()
    }

    @Test
    fun testAgentWorker_withValidPrompt_returnsSuccess() = runBlocking {
        every { useCase.invoke(any(), any()) } returns flowOf(AgentOrchestratorState.Completed("Done"))

        val worker = TestListenableWorkerBuilder<AgentWorker>(context)
            .setInputData(Data.Builder().putString(AgentWorker.KEY_PROMPT, "test prompt").build())
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters
                    ): ListenableWorker {
                        return AgentWorker(appContext, workerParameters, useCase)
                    }
                }
            )
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testAgentWorker_withNullPrompt_returnsFailure() = runBlocking {
        val worker = TestListenableWorkerBuilder<AgentWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters
                    ): ListenableWorker {
                        return AgentWorker(appContext, workerParameters, useCase)
                    }
                }
            )
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
