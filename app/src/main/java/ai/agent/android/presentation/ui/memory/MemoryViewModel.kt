package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import ai.agent.android.domain.usecases.EstimateCompactionUseCase
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import ai.agent.android.domain.usecases.MemoryCompactionUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.agent.android.domain.usecases.SaveMessageToMemoryUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.screens.memory.MemoryCategory
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStream
import javax.inject.Inject

/**
 * ViewModel for the redesigned long-term-memory screen.
 *
 * Owns the loaded chunk list, the view selections (category / sort / date /
 * semantic search), inline edit + tag editing, manual add, manual compaction
 * (with a pre-run estimate), pin toggling, and full export. The screen maps the
 * exposed [MemoryUiState] to the catalog surface.
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val exportMemoryBaseUseCase: ExportMemoryBaseUseCase,
    private val saveMessageToMemoryUseCase: SaveMessageToMemoryUseCase,
    private val memoryCompactionUseCase: MemoryCompactionUseCase,
    private val estimateCompactionUseCase: EstimateCompactionUseCase,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState(isLoading = true))

    /** The current UI state of the Memory screen. */
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val _exportRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot signal asking the screen to open the SAF document picker for full export. */
    val exportRequests: SharedFlow<Unit> = _exportRequests.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadAllData()
    }

    /** Loads chunks, table size, last-compacted time, and originating session names. */
    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val memories = memoryRepository.getAllMemories()
            val totalBytes = memoryRepository.observeStats().first().totalBytes
            val lastCompactedAt = settingsRepository.memoryLastCompactedAt.first()
            val sessionNames =
                resolveSessionNames(memories.mapNotNull { (it.source as? MemorySource.ChatSession)?.sessionId })
            _uiState.update {
                it.copy(
                    memories = memories,
                    totalBytes = totalBytes,
                    lastCompactedAt = lastCompactedAt,
                    sessionNames = sessionNames,
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun resolveSessionNames(ids: List<String>): Map<String, String> = ids.distinct().mapNotNull { id ->
        chatRepository.getSessionById(id)?.let { id to it.name }
    }.toMap()

    /** Selects a category chip. */
    fun selectCategory(category: MemoryCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** Sets the sort mode. */
    fun setSortMode(mode: MemorySortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    /** Sets the date-range filter. */
    fun setDateFilter(filter: MemoryDateFilter) {
        _uiState.update { it.copy(dateFilter = filter) }
    }

    /** Opens the semantic-search field. */
    fun openSearch() {
        _uiState.update { it.copy(searchActive = true, sortMode = MemorySortMode.Relevance) }
    }

    /** Closes search and clears the query/results. */
    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchActive = false, searchQuery = "", searchResults = null, sortMode = MemorySortMode.Recent)
        }
    }

    /**
     * Updates the search query and (debounced) runs a semantic search with a
     * permissive threshold so the surface shows a broad, relevance-ranked list.
     */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = try {
                retrieveRelevantMemoryUseCase.retrieveScored(query = query, limit = SEARCH_RESULT_LIMIT, threshold = 0f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Semantic memory search failed")
                emptyList()
            }
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    /** Opens the detail sheet for [id] (read mode). */
    fun openEntry(id: Long) {
        _uiState.update { it.copy(expandedId = id, editing = false) }
    }

    /** Closes the detail sheet. */
    fun closeEntry() {
        _uiState.update { it.copy(expandedId = null, editing = false) }
    }

    /** Switches the open detail sheet to edit mode. */
    fun editEntry(id: Long) {
        _uiState.update { it.copy(expandedId = id, editing = true) }
    }

    /** Leaves edit mode, back to read mode. */
    fun cancelEdit() {
        _uiState.update { it.copy(editing = false) }
    }

    /**
     * Commits an edit: re-embeds [body] with the active provider, persists the
     * text + embedding, replaces the tag list, then reloads. A re-embed failure
     * leaves the chunk untouched.
     */
    fun commitEdit(id: Long, body: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val embedding = embeddingProviderResolver.resolve().embed(body)
                memoryRepository.updateMemory(id = id, text = body, embedding = embedding)
                memoryRepository.setMemoryTags(id = id, tags = tags)
                _uiState.update { it.copy(editing = false, expandedId = null) }
                loadAllData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to commit memory edit for $id")
                _uiState.update { it.copy(editing = false) }
            }
        }
    }

    /** Deletes the entry and closes the sheet. */
    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            _uiState.update { it.copy(expandedId = null, editing = false) }
            loadAllData()
        }
    }

    /** Toggles the pinned flag of [id]. */
    fun togglePin(id: Long) {
        viewModelScope.launch {
            val current = _uiState.value.memories.firstOrNull { it.id == id } ?: return@launch
            memoryRepository.setMemoryPinned(id = id, pinned = !current.isPinned)
            loadAllData()
        }
    }

    /** Opens the Compact confirm dialog and loads the estimate. */
    fun showCompactDialog() {
        _uiState.update { it.copy(compactDialogVisible = true, compactEstimate = null) }
        viewModelScope.launch {
            val estimate = runCatching { estimateCompactionUseCase() }.getOrNull()
            _uiState.update { it.copy(compactEstimate = estimate) }
        }
    }

    /** Dismisses the Compact dialog. */
    fun dismissCompactDialog() {
        _uiState.update { it.copy(compactDialogVisible = false) }
    }

    /** Runs the real compaction pass, then reloads. */
    fun confirmCompact() {
        _uiState.update { it.copy(compactDialogVisible = false, isLoading = true) }
        viewModelScope.launch {
            runCatching { memoryCompactionUseCase() }
                .onFailure { Timber.w(it, "Manual compaction failed") }
            loadAllData()
        }
    }

    /** Opens the Add-memory dialog. */
    fun showAddDialog() {
        _uiState.update { it.copy(addDialogVisible = true) }
    }

    /** Dismisses the Add-memory dialog. */
    fun dismissAddDialog() {
        _uiState.update { it.copy(addDialogVisible = false) }
    }

    /** Saves a manual memory entry from [text], then reloads. */
    fun confirmAdd(text: String) {
        _uiState.update { it.copy(addDialogVisible = false) }
        viewModelScope.launch {
            saveMessageToMemoryUseCase(text)
            loadAllData()
        }
    }

    /** Raises [exportRequests] so the screen opens the SAF picker for a full export. */
    fun requestExportAll() {
        _exportRequests.tryEmit(Unit)
    }

    /** Serialises every chunk into the SAF-provided [target]. */
    fun exportAllTo(target: OutputStream) {
        viewModelScope.launch {
            runCatching { exportMemoryBaseUseCase(target = target, ids = null) }
                .onFailure { Timber.w(it, "Memory export failed") }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_RESULT_LIMIT = 50
    }
}
