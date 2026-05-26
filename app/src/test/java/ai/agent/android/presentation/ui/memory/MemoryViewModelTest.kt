package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
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
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var textEmbeddingEngine: TextEmbeddingEngine
    private lateinit var viewModel: MemoryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        textEmbeddingEngine = mockk(relaxed = true)
        coEvery { settingsRepository.maxMemoryChunksForSearch } returns flowOf(1000)
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
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
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
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteChatSession("session-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.deleteSession("session-1") }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() } // 1 from init, 1 from reload
    }

    @Test
    fun `deleteChatMessage calls repository and reloads data`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteChatMessage(100L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.deleteMessage(100L) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `deleteVectorMemory calls repository and reloads data`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteVectorMemory(200L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(200L) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `compactMemory calls repository and reloads data`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.compactMemory()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.compactMemory(1000) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `setTab updates currentTab state`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)

        viewModel.setTab(1)
        assertEquals(1, viewModel.uiState.value.currentTab)

        viewModel.setTab(0)
        assertEquals(0, viewModel.uiState.value.currentTab)
    }

    @Test
    fun `editVectorMemory regenerates embedding and persists update`() = runTest {
        val newEmbedding = floatArrayOf(0.42f, -0.13f, 0.99f)
        coEvery { textEmbeddingEngine.generateEmbedding("edited body") } returns newEmbedding

        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.editVectorMemory(id = 11L, newText = "edited body")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { textEmbeddingEngine.generateEmbedding("edited body") }
        coVerify(exactly = 1) {
            memoryRepository.updateMemory(id = 11L, text = "edited body", embedding = newEmbedding)
        }
        // Reload after persistence: init + reload = at least 2 calls.
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `togglePinned flips current pinned state and persists`() = runTest {
        val unpinned = MemoryChunk(id = 5L, text = "x", embedding = floatArrayOf(0f), timestamp = 1L, isPinned = false)
        coEvery { memoryRepository.getAllMemories() } returns listOf(unpinned)

        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 5L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 5L, pinned = true) }
    }

    @Test
    fun `togglePinned unpins an already pinned chunk`() = runTest {
        val pinned = MemoryChunk(id = 9L, text = "p", embedding = floatArrayOf(0f), timestamp = 2L, isPinned = true)
        coEvery { memoryRepository.getAllMemories() } returns listOf(pinned)

        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 9L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 9L, pinned = false) }
    }

    @Test
    fun `togglePinned ignores unknown ids`() = runTest {
        viewModel = MemoryViewModel(chatRepository, memoryRepository, settingsRepository, textEmbeddingEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 9999L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.setMemoryPinned(any(), any()) }
    }
}
