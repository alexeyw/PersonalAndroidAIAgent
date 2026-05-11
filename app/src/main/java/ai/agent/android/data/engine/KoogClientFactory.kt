package ai.agent.android.data.engine

import ai.agent.android.domain.engine.CloudLlmClientFactory
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Koog LLM client instances (LLMClients).
 * It uses the [ApiKeyRepository] to retrieve the necessary credentials
 * and configurations (like Custom Base URL for Ollama) at runtime.
 *
 * Implements the domain-level [CloudLlmClientFactory] interface so that
 * `CloudLlmNodeExecutor` can construct cloud clients without importing data-layer types,
 * while internal callers (e.g. `DelegateTaskTool`) retain the typed per-provider helpers.
 */
@Singleton
class KoogClientFactory @Inject constructor(private val apiKeyRepository: ApiKeyRepository) : CloudLlmClientFactory {

    /**
     * Provider-keyed dispatch used by domain-side consumers. Exhaustive on
     * [CloudProvider]; adding a new provider value forces an update here.
     *
     * @param provider The typed [CloudProvider] to construct a client for.
     * @return The LLMClient on success or `null` if credentials are missing.
     */
    override suspend fun createClient(provider: CloudProvider): Any? = when (provider) {
        CloudProvider.OPENAI -> createOpenAIExecutor()
        CloudProvider.ANTHROPIC -> createAnthropicExecutor()
        CloudProvider.GOOGLE -> createGoogleExecutor()
        CloudProvider.DEEPSEEK -> createDeepSeekExecutor()
        CloudProvider.OLLAMA -> createOllamaExecutor()
    }

    /**
     * Creates an OpenAI LLMClient.
     * @return The client, or null if the API key is not configured.
     */
    suspend fun createOpenAIExecutor(): LLMClient? {
        val key = apiKeyRepository.getOpenAIKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return OpenAILLMClient(apiKey = key)
    }

    /**
     * Creates an Anthropic LLMClient.
     * @return The client, or null if the API key is not configured.
     */
    suspend fun createAnthropicExecutor(): LLMClient? {
        val key = apiKeyRepository.getAnthropicKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return AnthropicLLMClient(apiKey = key)
    }

    /**
     * Creates a Google (Gemini) LLMClient.
     * @return The client, or null if the API key is not configured.
     */
    suspend fun createGoogleExecutor(): LLMClient? {
        val key = apiKeyRepository.getGoogleKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return GoogleLLMClient(apiKey = key)
    }

    /**
     * Creates a DeepSeek LLMClient.
     * @return The client, or null if the API key is not configured.
     */
    suspend fun createDeepSeekExecutor(): LLMClient? {
        val key = apiKeyRepository.getDeepSeekKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return DeepSeekLLMClient(apiKey = key)
    }

    /**
     * Creates an Ollama LLMClient connected to the configured local server.
     * @return The client, or null if the custom URL is not configured.
     */
    suspend fun createOllamaExecutor(): LLMClient? {
        val url = apiKeyRepository.getOllamaBaseUrl().firstOrNull()
        if (url.isNullOrBlank()) return null
        return OllamaClient(baseUrl = url)
    }
}
