package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentOrchestratorUseCaseTest {

    private lateinit var taskQueueManager: TaskQueueManager
    private lateinit var useCase: AgentOrchestratorUseCase

    private val sessionId = "test-session"

    @Before
    fun setup() {
        taskQueueManager = mockk(relaxed = true)
        useCase = AgentOrchestratorUseCase(taskQueueManager)
    }

    @Test
    fun `invoke enqueues task and returns state flow`() = runTest {
        val userPrompt = "Hello"

        every { taskQueueManager.observeTaskState(sessionId) } returns flowOf(
            AgentOrchestratorState.Loading,
            AgentOrchestratorState.Completed("Done")
        )

        val states = useCase(sessionId, userPrompt).toList()

        verify { taskQueueManager.enqueueTask(match { it.sessionId == sessionId && it.prompt == userPrompt }) }
        
        assertTrue(states[0] is AgentOrchestratorState.Loading)
        assertTrue(states[1] is AgentOrchestratorState.Completed)
    }

    @Test
    fun `resumeWithApproval delegates to TaskQueueManager`() {
        useCase.resumeWithApproval(sessionId, true)
        verify { taskQueueManager.resumeWithApproval(sessionId, true) }
    }
}
