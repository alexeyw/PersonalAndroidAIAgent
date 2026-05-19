package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val textEmbeddingEngine: TextEmbeddingEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState(isLoading = true))

    /**
     * The current UI state of the Memory screen.
     */
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

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
     * Replaces the body of a long-term memory chunk. Recomputes the
     * embedding from the new text via [TextEmbeddingEngine] before persisting
     * — re-embedding is required so semantic-search results stay coherent
     * with the visible text.
     *
     * @param id Identifier of the chunk to update.
     * @param newText The new raw text content committed by the user.
     */
    fun editVectorMemory(id: Long, newText: String) {
        viewModelScope.launch {
            val newEmbedding = textEmbeddingEngine.generateEmbedding(newText)
            memoryRepository.updateMemory(id = id, text = newText, embedding = newEmbedding)
            loadAllData()
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
}
