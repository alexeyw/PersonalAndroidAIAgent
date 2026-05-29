package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySourceFilter

/**
 * Represents the UI state for the Memory management screen.
 *
 * @property chatSessions A map where the key is the session ID and the value is a list of chat messages for that session.
 * @property vectorMemories A list of all stored long-term memory chunks (vector embeddings).
 * @property isLoading True if data is currently being fetched from the database.
 * @property currentTab The index of the currently selected tab (0 for Chat History, 1 for Vector Database).
 * @property dateFilter Active date-range filter applied to [vectorMemories] before display.
 * @property sourceFilters Active provenance filters; an empty set means "every source".
 * @property pinnedOnly When `true`, only pinned chunks are shown.
 * @property selectionMode When `true`, the screen is in multi-select mode.
 * @property selectedIds Ids of the chunks currently selected in multi-select mode.
 */
data class MemoryUiState(
    val chatSessions: Map<String, List<ChatMessage>> = emptyMap(),
    val vectorMemories: List<MemoryChunk> = emptyList(),
    val isLoading: Boolean = false,
    val currentTab: Int = 0,
    val dateFilter: MemoryDateFilter = MemoryDateFilter.All,
    val sourceFilters: Set<MemorySourceFilter> = emptySet(),
    val pinnedOnly: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)
