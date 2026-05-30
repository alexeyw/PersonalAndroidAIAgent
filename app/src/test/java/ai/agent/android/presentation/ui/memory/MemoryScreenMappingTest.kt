package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import app.knotwork.design.screens.memory.MemoryCategory
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySourceKind
import app.knotwork.design.screens.memory.MemoryVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [toViewState] — the pure projection from [MemoryUiState] to the
 * catalog `MemoryViewState` (filtering, grouping, breakdown, detail labels).
 */
class MemoryScreenMappingTest {

    private val now = 1_000_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun chunk(
        id: Long,
        ageDays: Long = 0,
        pinned: Boolean = false,
        source: MemorySource = MemorySource.Manual,
        text: String = "chunk $id",
        useCount: Int = 0,
        lastUsedAt: Long? = null,
        tags: List<String> = emptyList(),
    ) = MemoryChunk(
        id = id,
        text = text,
        embedding = floatArrayOf(0f),
        timestamp = now - ageDays * dayMs,
        isPinned = pinned,
        source = source,
        tags = tags,
        useCount = useCount,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun `empty memories yields Empty visual state`() {
        val vs = MemoryUiState().toViewState(now)
        assertEquals(MemoryVisualState.Empty, vs.visualState)
    }

    @Test
    fun `header breakdown counts sources and formats percentages`() {
        val state = MemoryUiState(
            memories = listOf(
                chunk(1, source = MemorySource.ChatSession("s")),
                chunk(2, source = MemorySource.ChatSession("s")),
                chunk(3, source = MemorySource.Manual),
                chunk(4, source = MemorySource.Compaction(emptyList())),
            ),
            totalBytes = 2_100_000L,
        )
        val vs = state.toViewState(now)
        assertEquals("4", vs.header.totalLabel)
        assertTrue(vs.header.sizeLabel.endsWith("MB"))
        // Auto 2/4 = 50%, Compact 1/4 = 25%, Manual 1/4 = 25%.
        val auto = vs.header.segments.first { it.kind == MemorySourceKind.Auto }
        assertEquals("AUTO 50 %", auto.label)
    }

    @Test
    fun `category chips carry per-source counts`() {
        val state = MemoryUiState(
            memories = listOf(
                chunk(1, pinned = true, source = MemorySource.Manual),
                chunk(2, source = MemorySource.ChatSession("s")),
            ),
        )
        val chips = state.toViewState(now).categoryChips.associate { it.category to it.count }
        assertEquals(2, chips[MemoryCategory.All])
        assertEquals(1, chips[MemoryCategory.Pinned])
        assertEquals(1, chips[MemoryCategory.Auto])
        assertEquals(1, chips[MemoryCategory.Manual])
    }

    @Test
    fun `Manual category filter keeps only manual chunks`() {
        val state = MemoryUiState(
            memories = listOf(chunk(1, source = MemorySource.Manual), chunk(2, source = MemorySource.ChatSession("s"))),
            selectedCategory = MemoryCategory.Manual,
        )
        val ids = state.toViewState(now).sections.flatMap { it.rows }.map { it.id }
        assertEquals(listOf("1"), ids)
    }

    @Test
    fun `Last7Days date filter drops older chunks`() {
        val state = MemoryUiState(
            memories = listOf(chunk(1, ageDays = 2), chunk(2, ageDays = 10)),
            dateFilter = MemoryDateFilter.Last7Days,
        )
        val ids = state.toViewState(now).sections.flatMap { it.rows }.map { it.id }
        assertEquals(listOf("1"), ids)
    }

    @Test
    fun `rows are grouped into Pinned Today and This week sections`() {
        val state = MemoryUiState(
            memories = listOf(
                chunk(1, pinned = true, ageDays = 0),
                chunk(2, ageDays = 0),
                chunk(3, ageDays = 3),
            ),
        )
        val titles = state.toViewState(now).sections.map { it.title }
        assertEquals(listOf("Pinned", "Today", "This week"), titles)
    }

    @Test
    fun `detail maps token count learned-from and used-in`() {
        val state = MemoryUiState(
            memories = listOf(
                chunk(
                    1,
                    source = MemorySource.ChatSession("s1"),
                    text = "12345678",
                    useCount = 6,
                    lastUsedAt = now - 2 * 60 * 60 * 1000,
                    tags = listOf("knowledge"),
                ),
            ),
            sessionNames = mapOf("s1" to "Pixel 9 NPU setup"),
            expandedId = 1L,
        )
        val detail = state.toViewState(now).expandedEntry!!
        assertEquals(MemoryVisualState.EntryExpanded, state.toViewState(now).visualState)
        assertEquals("2 tok", detail.tokenLabel) // 8 chars / 4
        assertEquals("Chat \"Pixel 9 NPU setup\"", detail.learnedFromLabel)
        assertTrue(detail.usedInLabel!!.startsWith("6 replies"))
        assertEquals(listOf("knowledge"), detail.tags)
    }

    @Test
    fun `never-used chunk has null used-in`() {
        val state = MemoryUiState(memories = listOf(chunk(1, useCount = 0)), expandedId = 1L)
        assertNull(state.toViewState(now).expandedEntry!!.usedInLabel)
    }

    @Test
    fun `searching state carries relevance scores on rows`() {
        val state = MemoryUiState(
            memories = listOf(chunk(1)),
            searchActive = true,
            searchQuery = "x",
            searchResults = listOf(chunk(1) to 0.97f),
        )
        val vs = state.toViewState(now)
        assertEquals(MemoryVisualState.Searching, vs.visualState)
        assertEquals("0.97", vs.sections.flatMap { it.rows }.first().relevanceScore)
    }
}
