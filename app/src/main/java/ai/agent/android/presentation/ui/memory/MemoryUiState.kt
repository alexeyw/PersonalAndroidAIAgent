package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk

/**
 * Represents the UI state for the Memory management screen.
 *
 * @property chatSessions A map where the key is the session ID and the value is a list of chat messages for that session.
 * @property vectorMemories A list of all stored long-term memory chunks (vector embeddings).
 * @property isLoading True if data is currently being fetched from the database.
 * @property currentTab The index of the currently selected tab (0 for Chat History, 1 for Vector Database).
 */
data class MemoryUiState(
    val chatSessions: Map<String, List<ChatMessage>> = emptyMap(),
    val vectorMemories: List<MemoryChunk> = emptyList(),
    val isLoading: Boolean = false,
    val currentTab: Int = 0,
)
