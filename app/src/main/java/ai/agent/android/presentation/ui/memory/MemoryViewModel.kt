package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
 * ViewModel for managing the state of the Memory screen.
 * Handles loading, displaying, and deleting short-term (chat history)
 * and long-term (vector database) memories.
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val exportMemoryBaseUseCase: ExportMemoryBaseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState(isLoading = true))

    /**
     * The current UI state of the Memory screen.
     */
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val _exportRequests: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)

    /**
     * One-shot signal raised when the user taps "Export selected" in
     * multi-select mode. The screen consumes it to launch the SAF
     * `CreateDocument` picker (ViewModels stay free of `Context`); the picked
     * stream is handed back via [exportSelectedTo].
     */
    val exportRequests: SharedFlow<Unit> = _exportRequests.asSharedFlow()

    init {
        loadAllData()
    }

    /**
     * Loads both chat sessions and vector memories from the local database.
     */
    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load long-term memories
            val memories = memoryRepository.getAllMemories()

            // Load chat sessions (short-term memory)
            val sessionIds = chatRepository.getAllSessions()
            val sessionsMap = mutableMapOf<String, List<ChatMessage>>()

            for (id in sessionIds) {
                // Using first() to get the current snapshot of messages for this session
                val messages = chatRepository.getMessagesForSession(id).first()
                if (messages.isNotEmpty()) {
                    sessionsMap[id] = messages
                }
            }

            _uiState.update {
                it.copy(
                    chatSessions = sessionsMap,
                    vectorMemories = memories,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Changes the currently active tab.
     *
     * @param index The index of the new tab.
     */
    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    /**
     * Deletes an entire chat session and its associated messages.
     *
     * @param sessionId The ID of the session to delete.
     */
    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            loadAllData() // Reload to reflect changes
        }
    }

    /**
     * Deletes a specific chat message by its ID.
     *
     * @param messageId The ID of the message to delete.
     */
    fun deleteChatMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
            loadAllData()
        }
    }

    /**
     * Deletes a specific vector memory chunk by its ID.
     *
     * @param memoryId The ID of the memory chunk to delete.
     */
    fun deleteVectorMemory(memoryId: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
            loadAllData()
        }
    }

    /**
     * Replaces the body of a long-term memory chunk. Recomputes the embedding
     * from the new text via the user's active embedding provider (resolved by
     * [EmbeddingProviderResolver]) before persisting — re-embedding is required
     * so semantic-search results stay coherent with the visible text, and using
     * the active provider keeps the edited chunk in the same embedding space as
     * the retrieval query.
     *
     * @param id Identifier of the chunk to update.
     * @param newText The new raw text content committed by the user.
     */
    fun editVectorMemory(id: Long, newText: String) {
        viewModelScope.launch {
            // Embedding may hit the network when a cloud provider is active.
            // Guard so a transient failure leaves the chunk untouched instead of
            // crashing the app; rethrow CancellationException to preserve
            // structured concurrency.
            try {
                val newEmbedding = embeddingProviderResolver.resolve().embed(newText)
                memoryRepository.updateMemory(id = id, text = newText, embedding = newEmbedding)
                loadAllData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to re-embed edited memory $id; keeping the previous content")
            }
        }
    }

    /**
     * Toggles the pinned state of a long-term memory chunk. The new state is
     * derived from the currently-loaded snapshot (`isPinned` flip), then
     * persisted; the surface reloads to reflect the change.
     *
     * @param id Identifier of the chunk whose pinned flag should flip.
     */
    fun togglePinned(id: Long) {
        viewModelScope.launch {
            val current = _uiState.value.vectorMemories.firstOrNull { it.id == id } ?: return@launch
            memoryRepository.setMemoryPinned(id = id, pinned = !current.isPinned)
            loadAllData()
        }
    }

    /**
     * Deletes the oldest memory chunks, keeping only the configured limit.
     */
    fun compactMemory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val limit = settingsRepository.maxMemoryChunksForSearch.first()
            memoryRepository.compactMemory(limit)
            loadAllData()
        }
    }

    /**
     * Sets the active date-range filter. Filtering itself is applied by the
     * screen against this persisted value; the VM only owns the selection so
     * it survives recomposition and configuration changes.
     */
    fun setDateFilter(filter: MemoryDateFilter) {
        _uiState.update { it.copy(dateFilter = filter) }
    }

    /**
     * Toggles a provenance filter on or off. An empty resulting set means
     * "every source" (the resting, unfiltered surface).
     */
    fun toggleSourceFilter(source: MemorySourceFilter) {
        _uiState.update { state ->
            val next = if (source in state.sourceFilters) {
                state.sourceFilters - source
            } else {
                state.sourceFilters + source
            }
            state.copy(sourceFilters = next)
        }
    }

    /** Flips the "pinned only" filter. */
    fun togglePinnedOnly() {
        _uiState.update { it.copy(pinnedOnly = !it.pinnedOnly) }
    }

    /**
     * Enters multi-select mode and selects [id] — invoked from a row
     * long-press.
     */
    fun enterSelection(id: Long) {
        _uiState.update { it.copy(selectionMode = true, selectedIds = setOf(id)) }
    }

    /**
     * Toggles selection of [id] while in multi-select mode. Deselecting the
     * last remaining entry exits selection mode so the user is not stranded on
     * an empty bulk-action bar.
     */
    fun toggleSelect(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            if (next.isEmpty()) {
                state.copy(selectionMode = false, selectedIds = emptySet())
            } else {
                state.copy(selectedIds = next)
            }
        }
    }

    /** Exits multi-select mode, clearing the current selection. */
    fun exitSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    /**
     * Deletes every selected chunk, then exits selection mode and reloads.
     */
    fun deleteSelected() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { memoryRepository.deleteMemory(it) }
            exitSelection()
            loadAllData()
        }
    }

    /**
     * Applies [pinned] to every selected chunk, then exits selection mode and
     * reloads. Backs the "Pin selected" / "Unpin selected" bulk actions.
     */
    fun setSelectedPinned(pinned: Boolean) {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { memoryRepository.setMemoryPinned(id = it, pinned = pinned) }
            exitSelection()
            loadAllData()
        }
    }

    /**
     * Raises [exportRequests] so the screen opens the SAF document picker.
     * No-op when nothing is selected.
     */
    fun requestExportSelected() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _exportRequests.tryEmit(Unit)
    }

    /**
     * Serialises the selected chunks into [target] (the SAF-provided stream),
     * then exits selection mode. Best-effort: a write failure is logged and
     * leaves the selection cleared so the user can retry from scratch.
     */
    fun exportSelectedTo(target: OutputStream) {
        val ids = _uiState.value.selectedIds
        viewModelScope.launch {
            try {
                exportMemoryBaseUseCase(target = target, ids = ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to export selected memories")
            } finally {
                exitSelection()
            }
        }
    }
}
