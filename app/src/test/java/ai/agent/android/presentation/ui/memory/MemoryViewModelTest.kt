package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySourceFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [MemoryViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var exportMemoryBaseUseCase: ExportMemoryBaseUseCase
    private lateinit var viewModel: MemoryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        embeddingProviderResolver = mockk(relaxed = true)
        embeddingProvider = mockk(relaxed = true)
        exportMemoryBaseUseCase = mockk(relaxed = true)
        coEvery { embeddingProviderResolver.resolve() } returns embeddingProvider
        coEvery { settingsRepository.maxMemoryChunksForSearch } returns flowOf(1000)
    }

    private fun createViewModel(): MemoryViewModel = MemoryViewModel(
        chatRepository,
        memoryRepository,
        settingsRepository,
        embeddingProviderResolver,
        exportMemoryBaseUseCase,
    )

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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteChatSession("session-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.deleteSession("session-1") }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() } // 1 from init, 1 from reload
    }

    @Test
    fun `deleteChatMessage calls repository and reloads data`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteChatMessage(100L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.deleteMessage(100L) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `deleteVectorMemory calls repository and reloads data`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteVectorMemory(200L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(200L) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `compactMemory calls repository and reloads data`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.compactMemory()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.compactMemory(1000) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `setTab updates currentTab state`() = runTest {
        viewModel = createViewModel()

        viewModel.setTab(1)
        assertEquals(1, viewModel.uiState.value.currentTab)

        viewModel.setTab(0)
        assertEquals(0, viewModel.uiState.value.currentTab)
    }

    @Test
    fun `editVectorMemory regenerates embedding and persists update`() = runTest {
        val newEmbedding = floatArrayOf(0.42f, -0.13f, 0.99f)
        coEvery { embeddingProvider.embed("edited body") } returns newEmbedding

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.editVectorMemory(id = 11L, newText = "edited body")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { embeddingProvider.embed("edited body") }
        coVerify(exactly = 1) {
            memoryRepository.updateMemory(id = 11L, text = "edited body", embedding = newEmbedding)
        }
        // Reload after persistence: init + reload = at least 2 calls.
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `editVectorMemory swallows embed failure and leaves the chunk unchanged`() = runTest {
        // A cloud-backed embed can fail with a network error; the ViewModel must
        // not crash and must not persist a half-applied edit.
        coEvery { embeddingProvider.embed("boom") } throws RuntimeException("network down")

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.editVectorMemory(id = 3L, newText = "boom")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `togglePinned flips current pinned state and persists`() = runTest {
        val unpinned = MemoryChunk(id = 5L, text = "x", embedding = floatArrayOf(0f), timestamp = 1L, isPinned = false)
        coEvery { memoryRepository.getAllMemories() } returns listOf(unpinned)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 5L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 5L, pinned = true) }
    }

    @Test
    fun `togglePinned unpins an already pinned chunk`() = runTest {
        val pinned = MemoryChunk(id = 9L, text = "p", embedding = floatArrayOf(0f), timestamp = 2L, isPinned = true)
        coEvery { memoryRepository.getAllMemories() } returns listOf(pinned)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 9L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 9L, pinned = false) }
    }

    @Test
    fun `togglePinned ignores unknown ids`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinned(id = 9999L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.setMemoryPinned(any(), any()) }
    }

    @Test
    fun `setDateFilter persists the selection`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setDateFilter(MemoryDateFilter.Last7Days)

        assertEquals(MemoryDateFilter.Last7Days, viewModel.uiState.value.dateFilter)
    }

    @Test
    fun `toggleSourceFilter adds then removes the source`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSourceFilter(MemorySourceFilter.Manual)
        assertEquals(setOf(MemorySourceFilter.Manual), viewModel.uiState.value.sourceFilters)

        viewModel.toggleSourceFilter(MemorySourceFilter.Manual)
        assertEquals(emptySet<MemorySourceFilter>(), viewModel.uiState.value.sourceFilters)
    }

    @Test
    fun `togglePinnedOnly flips the flag`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePinnedOnly()
        assertTrue(viewModel.uiState.value.pinnedOnly)

        viewModel.togglePinnedOnly()
        assertFalse(viewModel.uiState.value.pinnedOnly)
    }

    @Test
    fun `enterSelection turns on selection mode with the long-pressed id`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelection(id = 7L)

        val state = viewModel.uiState.value
        assertTrue(state.selectionMode)
        assertEquals(setOf(7L), state.selectedIds)
    }

    @Test
    fun `toggleSelect adds an id and deselecting the last exits selection`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelection(id = 1L)
        viewModel.toggleSelect(id = 2L)
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelect(id = 1L)
        viewModel.toggleSelect(id = 2L)

        val state = viewModel.uiState.value
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `deleteSelected removes every selected chunk and exits selection`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelection(id = 1L)
        viewModel.toggleSelect(id = 2L)
        viewModel.deleteSelected()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(1L) }
        coVerify(exactly = 1) { memoryRepository.deleteMemory(2L) }
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `setSelectedPinned pins every selected chunk and exits selection`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelection(id = 3L)
        viewModel.toggleSelect(id = 4L)
        viewModel.setSelectedPinned(pinned = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 3L, pinned = true) }
        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 4L, pinned = true) }
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `requestExportSelected emits only when something is selected`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Subscribe eagerly (Unconfined) so the collector is attached before the
        // one-shot `tryEmit`s fire — a StandardTestDispatcher collector would
        // otherwise miss the replay-less SharedFlow emission.
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.exportRequests.collect { events.add(it) }
        }

        viewModel.requestExportSelected() // nothing selected → no event
        assertEquals(0, events.size)

        viewModel.enterSelection(id = 9L)
        viewModel.requestExportSelected()
        assertEquals(1, events.size)
    }

    @Test
    fun `exportSelectedTo forwards the selected ids to the export use case and exits selection`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enterSelection(id = 5L)
        viewModel.toggleSelect(id = 6L)
        val stream = ByteArrayOutputStream()
        viewModel.exportSelectedTo(stream)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { exportMemoryBaseUseCase(target = stream, ids = setOf(5L, 6L)) }
        assertFalse(viewModel.uiState.value.selectionMode)
    }
}
