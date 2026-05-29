package ai.agent.android.data.services.embedding

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.services.EmbeddingException
import ai.agent.android.domain.services.EmbeddingProvider
import ai.koog.prompt.executor.clients.LLMEmbeddingProviderAPI
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OllamaEmbeddingProvider].
 *
 * The Koog Ollama client is faked via [KoogEmbedderFactory]; the on-device
 * fallback is a real [UseEmbeddingProvider] over a mocked engine.
 */
class OllamaEmbeddingProviderTest {

    private val embedderFactory = mockk<KoogEmbedderFactory>()
    private val apiKeyRepository = mockk<ApiKeyRepository>()
    private val fallbackEngine = mockk<TextEmbeddingEngine>()
    private val fallback = UseEmbeddingProvider(fallbackEngine)
    private val client = mockk<LLMEmbeddingProviderAPI>()

    private lateinit var provider: OllamaEmbeddingProvider

    @Before
    fun setup() {
        provider = OllamaEmbeddingProvider(embedderFactory, apiKeyRepository, fallback)
    }

    @Test
    fun `exposes the Ollama identity and 768 dimension`() {
        assertEquals(EmbeddingProvider.ID_OLLAMA, provider.id)
        assertEquals(768, provider.dimension)
    }

    @Test
    fun `embed with a base url calls Ollama and maps doubles to floats`() = runTest {
        every { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://192.168.1.2:11434")
        every { embedderFactory.ollamaClient("http://192.168.1.2:11434") } returns client
        coEvery { client.embed(any<List<String>>(), any()) } returns
            listOf(listOf(0.5, 0.25))

        val result = provider.embed("hi")

        assertArrayEquals(floatArrayOf(0.5f, 0.25f), result, 1e-6f)
        coVerify(exactly = 1) { client.embed(listOf("hi"), any()) }
    }

    @Test
    fun `embed with no base url falls back to the on-device provider`() = runTest {
        every { apiKeyRepository.getOllamaBaseUrl() } returns flowOf(null)
        coEvery { fallbackEngine.generateEmbedding("hi") } returns floatArrayOf(3f)

        val result = provider.embed("hi")

        assertArrayEquals(floatArrayOf(3f), result, 0f)
        coVerify(exactly = 0) { embedderFactory.ollamaClient(any()) }
    }

    @Test
    fun `embed wraps a client failure in EmbeddingException`() = runTest {
        every { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://host:11434")
        every { embedderFactory.ollamaClient(any()) } returns client
        coEvery { client.embed(any<List<String>>(), any()) } throws RuntimeException("down")

        val thrown = runCatching { provider.embed("hi") }.exceptionOrNull()

        assertTrue(thrown is EmbeddingException)
    }

    @Test
    fun `embed empty batch returns empty without a url lookup`() = runTest {
        val result = provider.embed(emptyList())

        assertEquals(0, result.size)
        coVerify(exactly = 0) { embedderFactory.ollamaClient(any()) }
    }
}
