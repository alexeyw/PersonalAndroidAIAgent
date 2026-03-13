package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var agentOrchestratorUseCase: AgentOrchestratorUseCase
    private lateinit var chatRepository: ChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentOrchestratorUseCase = mockk()
        chatRepository = mockk()

        // Mock chat repository flow for any session
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should generate session id and load messages`() = runTest {
        val messages = listOf(
            ChatMessage(sessionId = "test-session", role = Role.USER, content = "Hello", timestamp = 1L)
        )
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(messages)
        
        viewModel = ChatViewModel(agentOrchestratorUseCase, chatRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentSessionId.isNotEmpty())
        assertEquals(messages, state.messages)
        assertFalse(state.isGenerating)
        assertNull(state.orchestratorState)
        assertNull(state.errorMessage)
    }

    @Test
    fun `sendMessage should ignore blank prompt`() = runTest {
        viewModel = ChatViewModel(agentOrchestratorUseCase, chatRepository)
        advanceUntilIdle()

        viewModel.sendMessage("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any()) }
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `sendMessage should update state to generating and collect orchestrator states`() = runTest {
        val sessionId = "test-session"
        val userPrompt = "Test prompt"
        
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        viewModel = ChatViewModel(agentOrchestratorUseCase, chatRepository)
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt) } returns flow {
            emit(AgentOrchestratorState.Loading)
            emit(AgentOrchestratorState.Thinking("Hmm"))
            emit(AgentOrchestratorState.Completed("Final answer"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        // Final state verification
        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertTrue(state.orchestratorState is AgentOrchestratorState.Completed)
        assertEquals("Final answer", (state.orchestratorState as AgentOrchestratorState.Completed).finalResponse)
        assertNull(state.errorMessage)
    }

    @Test
    fun `sendMessage should handle error from orchestrator`() = runTest {
        val userPrompt = "Test prompt"
        val exceptionMessage = "Network failure"
        
        viewModel = ChatViewModel(agentOrchestratorUseCase, chatRepository)
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt) } returns flow {
            throw RuntimeException(exceptionMessage)
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertEquals(exceptionMessage, state.errorMessage)
        assertTrue(state.orchestratorState is AgentOrchestratorState.Error)
        assertEquals(exceptionMessage, (state.orchestratorState as AgentOrchestratorState.Error).message)
    }

    @Test
    fun `clearError should set errorMessage to null`() = runTest {
        viewModel = ChatViewModel(agentOrchestratorUseCase, chatRepository)
        advanceUntilIdle()

        // Force an error state
        val actualSessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(actualSessionId, "trigger error") } returns flow {
            throw RuntimeException("Error occurred")
        }

        viewModel.sendMessage("trigger error")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
