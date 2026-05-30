package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.MemoryStats
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import ai.agent.android.domain.usecases.CompactionEstimate
import ai.agent.android.domain.usecases.EstimateCompactionUseCase
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import ai.agent.android.domain.usecases.MemoryCompactionUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.agent.android.domain.usecases.SaveMessageToMemoryUseCase
import ai.agent.android.domain.usecases.SaveToMemoryOutcome
import app.knotwork.design.screens.memory.MemoryCategory
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySortMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var exportMemoryBaseUseCase: ExportMemoryBaseUseCase
    private lateinit var saveMessageToMemoryUseCase: SaveMessageToMemoryUseCase
    private lateinit var memoryCompactionUseCase: MemoryCompactionUseCase
    private lateinit var estimateCompactionUseCase: EstimateCompactionUseCase
    private lateinit var retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase
    private lateinit var viewModel: MemoryViewModel

    private val testDispatcher = StandardTestDispatcher()

    private fun chunk(id: Long, pinned: Boolean = false, source: MemorySource = MemorySource.Manual) = MemoryChunk(
        id = id,
        text = "chunk $id",
        embedding = floatArrayOf(0f),
        timestamp = id,
        isPinned = pinned,
        source = source,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        embeddingProviderResolver = mockk(relaxed = true)
        embeddingProvider = mockk(relaxed = true)
        exportMemoryBaseUseCase = mockk(relaxed = true)
        saveMessageToMemoryUseCase = mockk(relaxed = true)
        memoryCompactionUseCase = mockk(relaxed = true)
        estimateCompactionUseCase = mockk(relaxed = true)
        retrieveRelevantMemoryUseCase = mockk(relaxed = true)

        coEvery { embeddingProviderResolver.resolve() } returns embeddingProvider
        coEvery { memoryRepository.getAllMemories() } returns emptyList()
        coEvery { memoryRepository.observeStats() } returns flowOf(MemoryStats(0, 0L, 0, null))
        coEvery { settingsRepository.memoryLastCompactedAt } returns flowOf(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MemoryViewModel = MemoryViewModel(
        chatRepository,
        memoryRepository,
        settingsRepository,
        embeddingProviderResolver,
        exportMemoryBaseUseCase,
        saveMessageToMemoryUseCase,
        memoryCompactionUseCase,
        estimateCompactionUseCase,
        retrieveRelevantMemoryUseCase,
    )

    private fun TestScope.collectMessages(): MutableList<MemoryMessage> {
        val events = mutableListOf<MemoryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messageEvents.collect { events.add(it) }
        }
        return events
    }

    @Test
    fun `loadAllData populates memories size and resolves session names`() = runTest {
        coEvery { memoryRepository.getAllMemories() } returns listOf(
            chunk(1, source = MemorySource.ChatSession("s1")),
            chunk(2),
        )
        coEvery { memoryRepository.observeStats() } returns flowOf(MemoryStats(2, 4096L, 0, null))
        coEvery { settingsRepository.memoryLastCompactedAt } returns flowOf(123L)
        coEvery { chatRepository.getSessionById("s1") } returns
            ChatSession(id = "s1", name = "Setup chat", updatedAt = 0)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loadFailed)
        assertEquals(2, state.memories.size)
        assertEquals(4096L, state.totalBytes)
        assertEquals(123L, state.lastCompactedAt)
        assertEquals("Setup chat", state.sessionNames["s1"])
    }

    @Test
    fun `selectCategory setSortMode setDateFilter persist selections`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectCategory(MemoryCategory.Manual)
        viewModel.setSortMode(MemorySortMode.Alphabetical)
        viewModel.setDateFilter(MemoryDateFilter.Last7Days)

        val state = viewModel.uiState.value
        assertEquals(MemoryCategory.Manual, state.selectedCategory)
        assertEquals(MemorySortMode.Alphabetical, state.sortMode)
        assertEquals(MemoryDateFilter.Last7Days, state.dateFilter)
    }

    @Test
    fun `openSearch switches to relevance and onSearchQueryChange runs semantic search`() = runTest {
        // Threshold is omitted so the use case honours the user's configured
        // memorySearchThreshold (its default-null path) rather than 0f.
        coEvery {
            retrieveRelevantMemoryUseCase.retrieveScored(query = "berlin", limit = any(), threshold = null)
        } returns listOf(chunk(1) to 0.91f)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openSearch()
        assertTrue(viewModel.uiState.value.searchActive)
        assertEquals(MemorySortMode.Relevance, viewModel.uiState.value.sortMode)

        viewModel.onSearchQueryChange("berlin")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.searchResults?.size)
        coVerify { retrieveRelevantMemoryUseCase.retrieveScored(query = "berlin", limit = any(), threshold = null) }
    }

    @Test
    fun `closeSearch clears query and results`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.openSearch()
        viewModel.onSearchQueryChange("x")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.closeSearch()

        val state = viewModel.uiState.value
        assertFalse(state.searchActive)
        assertEquals("", state.searchQuery)
        assertEquals(null, state.searchResults)
    }

    @Test
    fun `openEntry editEntry cancelEdit toggle the detail state`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openEntry(5L)
        assertEquals(5L, viewModel.uiState.value.expandedId)
        assertFalse(viewModel.uiState.value.editing)

        viewModel.editEntry(5L)
        assertTrue(viewModel.uiState.value.editing)

        viewModel.cancelEdit()
        assertFalse(viewModel.uiState.value.editing)

        viewModel.closeEntry()
        assertEquals(null, viewModel.uiState.value.expandedId)
    }

    @Test
    fun `commitEdit re-embeds and persists text plus tags atomically then reloads`() = runTest {
        val embedding = floatArrayOf(0.3f)
        coEvery { embeddingProvider.embed("new body") } returns embedding

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.commitEdit(id = 7L, body = "new body", tags = listOf("tag"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            memoryRepository.updateMemoryWithTags(
                id = 7L,
                text = "new body",
                embedding = embedding,
                tags = listOf("tag"),
            )
        }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `commitEdit on embed failure emits an EditError and keeps the edit sheet open`() = runTest {
        coEvery { embeddingProvider.embed(any<String>()) } throws RuntimeException("offline")

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val messages = collectMessages()
        viewModel.editEntry(7L)

        viewModel.commitEdit(id = 7L, body = "boom", tags = emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.updateMemoryWithTags(any(), any(), any(), any()) }
        assertEquals(listOf(MemoryMessage.EditError), messages)
        // The draft survives: the sheet stays open in edit mode.
        assertEquals(7L, viewModel.uiState.value.expandedId)
        assertTrue(viewModel.uiState.value.editing)
    }

    @Test
    fun `deleteEntry removes the chunk and reloads`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteEntry(9L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(9L) }
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `togglePin flips current pinned state`() = runTest {
        coEvery { memoryRepository.getAllMemories() } returns listOf(chunk(3, pinned = false))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePin(3L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.setMemoryPinned(id = 3L, pinned = true) }
    }

    @Test
    fun `showCompactDialog loads estimate and confirmCompact runs the pass`() = runTest {
        coEvery { estimateCompactionUseCase(any()) } returns
            CompactionEstimate(estimatedRemoved = 5, estimatedFreedBytes = 100L, estimatedRuntimeSeconds = 2)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompactDialog()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.compactDialogVisible)
        assertEquals(5, viewModel.uiState.value.compactEstimate?.estimatedRemoved)

        viewModel.confirmCompact()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.compactDialogVisible)
        coVerify(exactly = 1) { memoryCompactionUseCase(any()) }
    }

    @Test
    fun `confirmAdd saves a manual entry then reloads`() = runTest {
        coEvery { saveMessageToMemoryUseCase("remember") } returns SaveToMemoryOutcome.Saved(1L)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmAdd("remember")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { saveMessageToMemoryUseCase("remember") }
        assertFalse(viewModel.uiState.value.addDialogVisible)
        coVerify(atLeast = 2) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `confirmAdd on Failed outcome emits an AddError and keeps the dialog open`() = runTest {
        coEvery { saveMessageToMemoryUseCase("boom") } returns SaveToMemoryOutcome.Failed(RuntimeException("offline"))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val messages = collectMessages()
        viewModel.showAddDialog()

        viewModel.confirmAdd("boom")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(MemoryMessage.AddError), messages)
        // The dialog stays open so the typed text survives for retry.
        assertTrue(viewModel.uiState.value.addDialogVisible)
        // Only the init load ran; a failed add does not trigger a reload.
        coVerify(exactly = 1) { memoryRepository.getAllMemories() }
    }

    @Test
    fun `requestExportAll emits and exportAllTo writes the full table`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.exportRequests.collect { events.add(it) }
        }
        viewModel.requestExportAll()
        assertEquals(1, events.size)

        val stream = ByteArrayOutputStream()
        viewModel.exportAllTo(stream)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { exportMemoryBaseUseCase(target = stream, ids = null, nowMillis = any()) }
    }
}
