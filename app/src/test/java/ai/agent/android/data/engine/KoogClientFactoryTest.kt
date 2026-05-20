package ai.agent.android.data.engine

import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [KoogClientFactory].
 */
class KoogClientFactoryTest {

    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var factory: KoogClientFactory

    @Before
    fun setup() {
        apiKeyRepository = mockk()
        settingsRepository = mockk(relaxed = true) {
            every { blockNetworkFromLocalModel } returns MutableStateFlow(false)
        }
        factory = KoogClientFactory(apiKeyRepository, settingsRepository)
    }

    @Test
    fun `createOpenAIExecutor returns null when key is null`() = runTest {
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        val executor = factory.createOpenAIExecutor()
        assertNull(executor)
    }

    @Test
    fun `createOpenAIExecutor returns executor when key is present`() = runTest {
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf("test-key")
        val executor = factory.createOpenAIExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `createAnthropicExecutor returns executor when key is present`() = runTest {
        coEvery { apiKeyRepository.getAnthropicKey() } returns flowOf("test-key")
        val executor = factory.createAnthropicExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `createGoogleExecutor returns executor when key is present`() = runTest {
        coEvery { apiKeyRepository.getGoogleKey() } returns flowOf("test-key")
        val executor = factory.createGoogleExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `createDeepSeekExecutor returns executor when key is present`() = runTest {
        coEvery { apiKeyRepository.getDeepSeekKey() } returns flowOf("test-key")
        val executor = factory.createDeepSeekExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `createOllamaExecutor returns executor when url is present`() = runTest {
        coEvery { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://localhost:11434")
        val executor = factory.createOllamaExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `createOllamaExecutor returns null when url is empty`() = runTest {
        coEvery { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("")
        val executor = factory.createOllamaExecutor()
        assertNull(executor)
    }

    @Test
    fun `createOpenAIExecutor returns null when local-only mode is on`() = runTest {
        every { settingsRepository.blockNetworkFromLocalModel } returns MutableStateFlow(true)
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf("test-key")
        val executor = factory.createOpenAIExecutor()
        assertNull(executor)
    }

    @Test
    fun `createAnthropicExecutor returns null when local-only mode is on`() = runTest {
        every { settingsRepository.blockNetworkFromLocalModel } returns MutableStateFlow(true)
        coEvery { apiKeyRepository.getAnthropicKey() } returns flowOf("test-key")
        assertNull(factory.createAnthropicExecutor())
    }

    @Test
    fun `Ollama is still reachable in local-only mode`() = runTest {
        every { settingsRepository.blockNetworkFromLocalModel } returns MutableStateFlow(true)
        coEvery { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://192.168.1.42:11434")
        assertNotNull(factory.createOllamaExecutor())
    }
}
