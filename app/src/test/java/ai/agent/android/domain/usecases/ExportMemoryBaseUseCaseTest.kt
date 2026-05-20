package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExportMemoryBaseUseCaseTest {

    @Test
    fun `invoke serialises every chunk into the target stream`() = runTest {
        val repo = mockk<MemoryRepository>()
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0.1f, 0.2f), timestamp = 1L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0.3f), timestamp = 2L, isPinned = true),
        )
        val out = ByteArrayOutputStream()
        val written = ExportMemoryBaseUseCase(repo)(out)
        val payload = JSONObject(out.toString(Charsets.UTF_8.name()))
        assertEquals(2, written)
        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals(2, payload.getJSONArray("chunks").length())
        assertEquals("alpha", payload.getJSONArray("chunks").getJSONObject(0).getString("text"))
        assertEquals(true, payload.getJSONArray("chunks").getJSONObject(1).getBoolean("isPinned"))
    }

    @Test
    fun `invoke returns zero for empty memory`() = runTest {
        val repo = mockk<MemoryRepository>()
        coEvery { repo.getAllMemories() } returns emptyList()
        val written = ExportMemoryBaseUseCase(repo)(ByteArrayOutputStream())
        assertEquals(0, written)
    }
}
