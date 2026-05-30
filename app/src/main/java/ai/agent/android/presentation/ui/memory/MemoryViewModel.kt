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
import ai.agent.android.domain.usecases.SaveToMemoryOutcome
import ai.agent.android.presentation.state.TransientMessageRelay
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
    private val transientMessageRelay: TransientMessageRelay,
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
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
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
                        errorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A read failure (DB/decryption) must surface the Error/Retry
                // state instead of leaving the screen stuck on a stale list.
                Timber.w(e, "Failed to load memory")
                _uiState.update { it.copy(isLoading = false, errorMessage = LOAD_ERROR_MESSAGE) }
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
     * Updates the search query and (debounced) runs a semantic search. The
     * user's configured relevance threshold is honoured (passing `null` lets
     * the use case read `memorySearchThreshold`) so results stay relevant
     * rather than returning the whole pool; a larger [SEARCH_RESULT_LIMIT] just
     * widens how many of those relevant hits the browse surface shows.
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
                retrieveRelevantMemoryUseCase.retrieveScored(query = query, limit = SEARCH_RESULT_LIMIT)
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
     * Commits an edit: re-embeds [body] with the active provider, then persists
     * the text, embedding, and tags **atomically** (one transaction) so the
     * write can never half-apply. On a re-embed or persistence failure the
     * chunk is left untouched, the user is told via a snackbar, and the surface
     * reloads so it reflects the database rather than a stale draft.
     */
    fun commitEdit(id: Long, body: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val embedding = embeddingProviderResolver.resolve().embed(body)
                memoryRepository.updateMemoryWithTags(id = id, text = body, embedding = embedding, tags = tags)
                _uiState.update { it.copy(editing = false, expandedId = null) }
                loadAllData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to commit memory edit for $id")
                transientMessageRelay.post(EDIT_ERROR_MESSAGE)
                _uiState.update { it.copy(editing = false, expandedId = null) }
                loadAllData()
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

    /**
     * Saves a manual memory entry from [text]. On success the surface reloads;
     * on a Failed outcome (e.g. an offline cloud embedding provider) the user is
     * told via a snackbar instead of the add silently vanishing. A blank input
     * (Skipped) is a no-op.
     */
    fun confirmAdd(text: String) {
        _uiState.update { it.copy(addDialogVisible = false) }
        viewModelScope.launch {
            when (saveMessageToMemoryUseCase(text)) {
                is SaveToMemoryOutcome.Saved -> loadAllData()
                is SaveToMemoryOutcome.Failed -> transientMessageRelay.post(ADD_ERROR_MESSAGE)
                SaveToMemoryOutcome.Skipped -> Unit
            }
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

        // User-visible snackbar messages routed through TransientMessageRelay.
        const val LOAD_ERROR_MESSAGE = "Couldn't load memory"
        const val ADD_ERROR_MESSAGE = "Couldn't save to memory"
        const val EDIT_ERROR_MESSAGE = "Couldn't save the edit"
    }
}
