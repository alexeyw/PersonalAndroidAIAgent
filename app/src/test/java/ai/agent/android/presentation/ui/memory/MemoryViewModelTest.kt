package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var viewModel: MemoryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadAllData populates state with memories and chat sessions`() = runTest {
        // Arrange
        val memories = listOf(MemoryChunk(1, "Memory 1", floatArrayOf(0.1f), 1000L))
        val sessionId = "session-1"
        val messages = listOf(ChatMessage(1, sessionId, Role.USER, "Hello", 1000L))
        
        coEvery { memoryRepository.getAllMemories() } returns memories
        coEvery { chatRepository.getAllSessions() } returns listOf(sessionId)
        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)

        // Act
        viewModel = MemoryViewModel(chatRepository, memoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(memories, state.vectorMemories)
        assertEquals(1, state.chatSessions.size)
        assertEquals(messages, state.chatSessions[sessionId])
    }

    @Test
    fun `deleteChatSession calls repository and reloads data`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteChatSession("session-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.deleteSession("session-1") }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() } // 1 from init, 1 from reload
    }
    
    @Test
    fun `setTab updates currentTab state`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository)
        
        viewModel.setTab(1)
        assertEquals(1, viewModel.uiState.value.currentTab)
        
        viewModel.setTab(0)
        assertEquals(0, viewModel.uiState.value.currentTab)
    }
}
