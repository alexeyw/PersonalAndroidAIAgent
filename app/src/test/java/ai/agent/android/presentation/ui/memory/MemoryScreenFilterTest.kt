package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemorySourceFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [applyFilters] — the pure provenance / date / pinned filter
 * applied by `MemoryScreen` before mapping chunks to rows.
 */
class MemoryScreenFilterTest {

    private val now = 1_000_000_000_000L
    private val dayMillis = 24L * 60 * 60 * 1000

    private fun chunk(
        id: Long,
        ageDays: Long = 0,
        source: MemorySource = MemorySource.Manual,
        pinned: Boolean = false,
    ): MemoryChunk = MemoryChunk(
        id = id,
        text = "chunk-$id",
        embedding = floatArrayOf(0f),
        timestamp = now - ageDays * dayMillis,
        isPinned = pinned,
        source = source,
    )

    @Test
    fun `given All date filter and empty sources then everything passes`() {
        val chunks = listOf(
            chunk(1, ageDays = 0, source = MemorySource.ChatSession("s")),
            chunk(2, ageDays = 100, source = MemorySource.Compaction(emptyList())),
            chunk(3, ageDays = 1, source = MemorySource.Unknown),
        )

        val result = chunks.applyFilters(
            dateFilter = MemoryDateFilter.All,
            sourceFilters = emptySet(),
            pinnedOnly = false,
            nowMillis = now,
        )

        assertEquals(chunks.map { it.id }, result.map { it.id })
    }

    @Test
    fun `given Last7Days then chunks older than 7 days are dropped`() {
        val chunks = listOf(chunk(1, ageDays = 2), chunk(2, ageDays = 10))

        val result = chunks.applyFilters(
            dateFilter = MemoryDateFilter.Last7Days,
            sourceFilters = emptySet(),
            pinnedOnly = false,
            nowMillis = now,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `given Manual source filter then only manual chunks pass`() {
        val chunks = listOf(
            chunk(1, source = MemorySource.Manual),
            chunk(2, source = MemorySource.ChatSession("s")),
            chunk(3, source = MemorySource.Compaction(emptyList())),
            chunk(4, source = MemorySource.Unknown),
        )

        val result = chunks.applyFilters(
            dateFilter = MemoryDateFilter.All,
            sourceFilters = setOf(MemorySourceFilter.Manual),
            pinnedOnly = false,
            nowMillis = now,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `given Auto and Compaction filters then ChatSession and Compaction pass but Unknown drops`() {
        val chunks = listOf(
            chunk(1, source = MemorySource.ChatSession("s")),
            chunk(2, source = MemorySource.Compaction(emptyList())),
            chunk(3, source = MemorySource.Unknown),
            chunk(4, source = MemorySource.Manual),
        )

        val result = chunks.applyFilters(
            dateFilter = MemoryDateFilter.All,
            sourceFilters = setOf(MemorySourceFilter.Auto, MemorySourceFilter.Compaction),
            pinnedOnly = false,
            nowMillis = now,
        )

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `given pinnedOnly then only pinned chunks pass`() {
        val chunks = listOf(chunk(1, pinned = true), chunk(2, pinned = false))

        val result = chunks.applyFilters(
            dateFilter = MemoryDateFilter.All,
            sourceFilters = emptySet(),
            pinnedOnly = true,
            nowMillis = now,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }
}
