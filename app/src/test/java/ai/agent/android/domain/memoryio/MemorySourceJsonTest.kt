package ai.agent.android.domain.memoryio

import ai.agent.android.domain.models.MemorySource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MemorySourceJson] — the shared `MemorySource` ↔ JSON codec
 * used by both the Room column converter and the memory export file.
 */
class MemorySourceJsonTest {

    @Test
    fun `encode then decode round-trips every variant`() {
        val variants = listOf(
            MemorySource.Manual,
            MemorySource.Unknown,
            MemorySource.ChatSession("sess-42"),
            MemorySource.Compaction(listOf(1L, 2L, 3L)),
        )
        variants.forEach { source ->
            assertEquals(source, MemorySourceJson.decode(MemorySourceJson.encode(source)))
        }
    }

    @Test
    fun `decode of null yields Unknown`() {
        assertEquals(MemorySource.Unknown, MemorySourceJson.decode(null))
    }

    @Test
    fun `encode stamps the stable wire type key`() {
        assertEquals(MemorySource.TYPE_MANUAL, MemorySourceJson.encode(MemorySource.Manual).getString("type"))
        assertEquals(
            MemorySource.TYPE_CHAT_SESSION,
            MemorySourceJson.encode(MemorySource.ChatSession("s")).getString("type"),
        )
    }
}
