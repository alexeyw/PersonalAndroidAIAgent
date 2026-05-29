package ai.agent.android.domain.services

import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EmbeddingProviderResolver].
 */
class EmbeddingProviderResolverTest {

    private val settingsRepository = mockk<SettingsRepository>()

    private val useProvider = fakeProvider(EmbeddingProvider.ID_USE, dimension = 512)
    private val cloudProvider = fakeProvider(EmbeddingProvider.ID_OPENAI_3_SMALL, dimension = 1536)
    private val ollamaProvider = fakeProvider(EmbeddingProvider.ID_OLLAMA, dimension = 768)

    private val providers = mapOf(
        EmbeddingProvider.ID_USE to useProvider,
        EmbeddingProvider.ID_OPENAI_3_SMALL to cloudProvider,
        EmbeddingProvider.ID_OLLAMA to ollamaProvider,
    )

    private lateinit var resolver: EmbeddingProviderResolver

    @Before
    fun setup() {
        resolver = EmbeddingProviderResolver(providers, settingsRepository)
    }

    @Test
    fun `given active id matches a provider when resolve then returns that provider`() = runTest {
        every { settingsRepository.activeEmbeddingProviderId } returns flowOf(EmbeddingProvider.ID_OPENAI_3_SMALL)

        val result = resolver.resolve()

        assertSame(cloudProvider, result)
        assertEquals(EmbeddingProvider.ID_OPENAI_3_SMALL, result.id)
    }

    @Test
    fun `given active id is the default when resolve then returns on-device provider`() = runTest {
        every { settingsRepository.activeEmbeddingProviderId } returns flowOf(EmbeddingProvider.ID_USE)

        val result = resolver.resolve()

        assertSame(useProvider, result)
    }

    @Test
    fun `given unknown active id when resolve then falls back to on-device provider`() = runTest {
        every { settingsRepository.activeEmbeddingProviderId } returns flowOf("removed_provider_v0")

        val result = resolver.resolve()

        assertSame(useProvider, result)
        assertEquals(EmbeddingProvider.ID_USE, result.id)
    }

    /**
     * Builds a lightweight stub [EmbeddingProvider] with a fixed [id] and
     * [dimension]; embedding methods are unused by the resolver, so they throw
     * if accidentally invoked.
     */
    private fun fakeProvider(id: String, dimension: Int): EmbeddingProvider = object : EmbeddingProvider {
        override val id: String = id
        override val displayName: String = id
        override val dimension: Int = dimension
        override suspend fun embed(text: String): FloatArray = error("not used")
        override suspend fun embed(texts: List<String>): List<FloatArray> = error("not used")
    }
}
