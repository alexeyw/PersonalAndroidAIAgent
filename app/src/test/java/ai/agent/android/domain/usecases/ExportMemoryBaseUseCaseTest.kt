package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExportMemoryBaseUseCaseTest {

    private fun useCase(
        repo: MemoryRepository,
        providerId: String = EmbeddingProvider.ID_USE,
    ): ExportMemoryBaseUseCase {
        val settings = mockk<SettingsRepository>()
        every { settings.activeEmbeddingProviderId } returns flowOf(providerId)
        return ExportMemoryBaseUseCase(repo, settings)
    }

    @Test
    fun `invoke serialises every chunk into the target stream`() = runTest {
        val repo = mockk<MemoryRepository>()
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0.1f, 0.2f), timestamp = 1L),
            MemoryChunk(
                id = 2,
                text = "beta",
                embedding = floatArrayOf(0.3f),
                timestamp = 2L,
                isPinned = true,
                source = MemorySource.Manual,
                tags = listOf("preference"),
            ),
        )
        val out = ByteArrayOutputStream()
        val written = useCase(repo, providerId = "openai_3_small")(out, nowMillis = 42L)
        val payload = JSONObject(out.toString(Charsets.UTF_8.name()))
        assertEquals(2, written)
        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals("openai_3_small", payload.getString("embeddingProviderId"))
        assertEquals(42L, payload.getLong("exportedAt"))
        assertEquals(2, payload.getJSONArray("chunks").length())
        val first = payload.getJSONArray("chunks").getJSONObject(0)
        assertEquals("alpha", first.getString("text"))
        val second = payload.getJSONArray("chunks").getJSONObject(1)
        assertEquals(true, second.getBoolean("isPinned"))
        assertEquals("manual", second.getJSONObject("source").getString("type"))
        assertEquals("preference", second.getJSONArray("tags").getString(0))
    }

    @Test
    fun `invoke writes only the requested ids when a subset is supplied`() = runTest {
        val repo = mockk<MemoryRepository>()
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0.1f), timestamp = 1L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0.3f), timestamp = 2L),
        )
        val out = ByteArrayOutputStream()
        val written = useCase(repo)(out, ids = setOf(2L))
        val payload = JSONObject(out.toString(Charsets.UTF_8.name()))
        assertEquals(1, written)
        assertEquals(1, payload.getJSONArray("chunks").length())
        assertEquals("beta", payload.getJSONArray("chunks").getJSONObject(0).getString("text"))
    }

    @Test
    fun `invoke returns zero for empty memory`() = runTest {
        val repo = mockk<MemoryRepository>()
        coEvery { repo.getAllMemories() } returns emptyList()
        val written = useCase(repo)(ByteArrayOutputStream())
        assertEquals(0, written)
    }
}
