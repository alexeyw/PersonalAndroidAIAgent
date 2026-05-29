package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SaveMessageToMemoryUseCase] — the direct-wrapper manual
 * save path behind the chat "Save to memory" action.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveMessageToMemoryUseCaseTest {

    private lateinit var resolver: EmbeddingProviderResolver
    private lateinit var provider: EmbeddingProvider
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var useCase: SaveMessageToMemoryUseCase

    @Before
    fun setup() {
        resolver = mockk()
        provider = mockk()
        memoryRepository = mockk()
        coEvery { resolver.resolve() } returns provider
        useCase = SaveMessageToMemoryUseCase(resolver, memoryRepository)
    }

    @Test
    fun `given non-blank text when invoked then embeds and stores as Manual`() = runTest {
        val embedding = floatArrayOf(0.1f, 0.2f)
        coEvery { provider.embed("Remember this") } returns embedding
        coEvery { memoryRepository.saveMemory("Remember this", embedding, MemorySource.Manual) } returns 42L

        val outcome = useCase("  Remember this  ")

        assertEquals(SaveToMemoryOutcome.Saved(id = 42L), outcome)
        coVerify(exactly = 1) {
            memoryRepository.saveMemory(text = "Remember this", embedding = embedding, source = MemorySource.Manual)
        }
    }

    @Test
    fun `given blank text when invoked then skipped without touching repository`() = runTest {
        val outcome = useCase("   ")

        assertEquals(SaveToMemoryOutcome.Skipped, outcome)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
    }

    @Test
    fun `given embed throws when invoked then returns Failed and does not store`() = runTest {
        val boom = RuntimeException("network down")
        coEvery { provider.embed(any<String>()) } throws boom

        val outcome = useCase("Remember this")

        assertTrue(outcome is SaveToMemoryOutcome.Failed)
        assertEquals(boom, (outcome as SaveToMemoryOutcome.Failed).cause)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
    }
}
