package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.NetworkState
import ai.agent.android.domain.models.RoutingDecision
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.NetworkStateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRouterUseCaseTest {

    private lateinit var networkStateRepository: NetworkStateRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var useCase: TaskRouterUseCase

    private val networkStateFlow = MutableStateFlow(NetworkState(isConnected = false, isWifiConnected = false))

    @Before
    fun setup() {
        networkStateRepository = mockk()
        apiKeyRepository = mockk()
        
        every { networkStateRepository.networkState } returns networkStateFlow
        
        // Default empty keys
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        coEvery { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        coEvery { apiKeyRepository.getAnthropicKey() } returns flowOf(null)
        coEvery { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)
        coEvery { apiKeyRepository.getOllamaBaseUrl() } returns flowOf(null)

        useCase = TaskRouterUseCase(networkStateRepository, apiKeyRepository)
    }

    @Test
    fun `private data keywords always route to LocalLiteRT regardless of network`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = true)
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf("key")

        val result = useCase("Tell me about my contacts")
        assertEquals(RoutingDecision.LocalLiteRT, result)
    }

    @Test
    fun `system keywords always route to LocalLiteRT`() = runTest {
        val result = useCase("Change system settings")
        assertEquals(RoutingDecision.LocalLiteRT, result)
    }

    @Test
    fun `complex task with no network routes to LocalLiteRT`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = false, isWifiConnected = false)
        val result = useCase("Explain this complex code")
        assertEquals(RoutingDecision.LocalLiteRT, result)
    }

    @Test
    fun `complex task with internet and OpenAI key routes to CloudLLM`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = false)
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-123")

        val result = useCase("Refactor my code")
        assertTrue(result is RoutingDecision.CloudLLM)
        assertEquals("openai", (result as RoutingDecision.CloudLLM).provider)
    }

    @Test
    fun `complex task with internet and Anthropic key routes to CloudLLM`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = false)
        coEvery { apiKeyRepository.getAnthropicKey() } returns flowOf("ant-123")

        val result = useCase("Analyze this architecture")
        assertTrue(result is RoutingDecision.CloudLLM)
        assertEquals("anthropic", (result as RoutingDecision.CloudLLM).provider)
    }

    @Test
    fun `complex task with wifi and Ollama url routes to LocalOllama`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = true)
        coEvery { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://localhost:11434")

        val result = useCase("Write a programming script")
        assertEquals(RoutingDecision.LocalOllama, result)
    }

    @Test
    fun `complex task with internet but no keys routes to LocalLiteRT`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = true)
        val result = useCase("Analyze my code")
        assertEquals(RoutingDecision.LocalLiteRT, result)
    }

    @Test
    fun `normal task without keywords routes to LocalLiteRT`() = runTest {
        networkStateFlow.value = NetworkState(isConnected = true, isWifiConnected = true)
        coEvery { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-123")

        val result = useCase("Hello, how are you?")
        assertEquals(RoutingDecision.LocalLiteRT, result)
    }
}
