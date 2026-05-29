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
 * Unit tests for [CloudEmbeddingProvider].
 *
 * The Koog client is faked via [KoogEmbedderFactory], so no real HTTP request
 * is ever issued. The on-device fallback is a **real** [UseEmbeddingProvider]
 * over a mocked engine, exercising the genuine fallback path.
 */
class CloudEmbeddingProviderTest {

    private val embedderFactory = mockk<KoogEmbedderFactory>()
    private val apiKeyRepository = mockk<ApiKeyRepository>()
    private val fallbackEngine = mockk<TextEmbeddingEngine>()
    private val fallback = UseEmbeddingProvider(fallbackEngine)
    private val client = mockk<LLMEmbeddingProviderAPI>()

    private lateinit var provider: CloudEmbeddingProvider

    @Before
    fun setup() {
        provider = CloudEmbeddingProvider(embedderFactory, apiKeyRepository, fallback)
    }

    @Test
    fun `exposes the OpenAI identity and 1536 dimension`() {
        assertEquals(EmbeddingProvider.ID_OPENAI_3_SMALL, provider.id)
        assertEquals(1536, provider.dimension)
    }

    @Test
    fun `embed with a key calls OpenAI and maps doubles to floats`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-test")
        every { embedderFactory.openAiClient("sk-test") } returns client
        coEvery { client.embed(any<List<String>>(), any()) } returns
            listOf(listOf(0.1, 0.2, 0.3))

        val result = provider.embed("hello")

        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), result, 1e-6f)
        coVerify(exactly = 1) { client.embed(listOf("hello"), any()) }
    }

    @Test
    fun `embed batch preserves per-input order`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-test")
        every { embedderFactory.openAiClient(any()) } returns client
        coEvery { client.embed(any<List<String>>(), any()) } returns
            listOf(listOf(1.0), listOf(2.0))

        val result = provider.embed(listOf("a", "b"))

        assertEquals(2, result.size)
        assertArrayEquals(floatArrayOf(1f), result[0], 0f)
        assertArrayEquals(floatArrayOf(2f), result[1], 0f)
    }

    @Test
    fun `embed with no key falls back to the on-device provider`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        coEvery { fallbackEngine.generateEmbedding("hello") } returns floatArrayOf(9f)

        val result = provider.embed("hello")

        assertArrayEquals(floatArrayOf(9f), result, 0f)
        coVerify(exactly = 0) { embedderFactory.openAiClient(any()) }
    }

    @Test
    fun `embed with a blank key falls back to the on-device provider`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf("   ")
        coEvery { fallbackEngine.generateEmbedding(any()) } returns floatArrayOf(7f)

        val result = provider.embed(listOf("x"))

        assertArrayEquals(floatArrayOf(7f), result[0], 0f)
    }

    @Test
    fun `embed wraps a client failure in EmbeddingException`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-test")
        every { embedderFactory.openAiClient(any()) } returns client
        coEvery { client.embed(any<List<String>>(), any()) } throws RuntimeException("boom")

        val thrown = runCatching { provider.embed("hello") }.exceptionOrNull()

        assertTrue(thrown is EmbeddingException)
        assertEquals("boom", thrown?.cause?.message)
    }

    @Test
    fun `embed empty batch returns empty without a key lookup`() = runTest {
        val result = provider.embed(emptyList())

        assertEquals(0, result.size)
        coVerify(exactly = 0) { embedderFactory.openAiClient(any()) }
    }
}
