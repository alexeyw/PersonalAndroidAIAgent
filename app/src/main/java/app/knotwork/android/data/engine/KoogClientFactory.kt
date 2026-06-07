package app.knotwork.android.data.engine

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
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
class KoogClientFactory @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val settingsRepository: SettingsRepository,
) : CloudLlmClientFactory {

    /**
     * Shared Ktor-backed HTTP factory used by every cloud client.
     *
     * Why explicit instead of SPI auto-discovery: Koog 1.0.0 declares
     * `KoogHttpClient.Factory` as a JVM ServiceLoader SPI, but the Maven
     * Central publish of `http-client-ktor-android-1.0.0` omits the
     * `META-INF/services/ai.koog.http.client.KoogHttpClient$Factory` registration
     * file (the Factory class is present, the registration is not). On the
     * first cloud call the default code path therefore throws
     * `IllegalStateException: No KoogHttpClient.Factory provider found on the
     * runtime classpath`. Constructing the Ktor factory directly bypasses the
     * SPI lookup entirely — the same workaround the Koog error message
     * suggests ("…or pass a KoogHttpClient.Factory explicitly"). Re-collapse
     * to the no-arg secondary constructor once Koog ships an AAR with the
     * services file restored.
     */
    private val httpClientFactory = KtorKoogHttpClient.Factory()

    /**
     * Provider-keyed dispatch used by domain-side consumers. Exhaustive on
     * [CloudProvider]; adding a new provider value forces an update here.
     *
     * When `SettingsRepository.blockNetworkFromLocalModel` is `true`, every
     * cloud provider (OpenAI / Anthropic / Google / DeepSeek) returns `null`
     * regardless of credential state — only the LAN-local Ollama client is
     * still constructible. The semantics mirror the Settings →
     * Restrictions → "Block network from local model" toggle.
     *
     * @param provider The typed [CloudProvider] to construct a client for.
     * @return The LLMClient on success or `null` if credentials are missing
     *   or local-only mode is on.
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
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createOpenAIExecutor(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getOpenAIKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return OpenAILLMClient(apiKey = key, httpClientFactory = httpClientFactory)
    }

    /**
     * Creates an Anthropic LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createAnthropicExecutor(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getAnthropicKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return AnthropicLLMClient(apiKey = key, httpClientFactory = httpClientFactory)
    }

    /**
     * Creates a Google (Gemini) LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createGoogleExecutor(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getGoogleKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return GoogleLLMClient(apiKey = key, httpClientFactory = httpClientFactory)
    }

    /**
     * Creates a DeepSeek LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createDeepSeekExecutor(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getDeepSeekKey().firstOrNull()
        if (key.isNullOrBlank()) return null
        return DeepSeekLLMClient(apiKey = key, httpClientFactory = httpClientFactory)
    }

    /**
     * Creates an Ollama LLMClient connected to the configured local server.
     * @return The client, or null if the custom URL is not configured.
     */
    suspend fun createOllamaExecutor(): LLMClient? {
        val url = apiKeyRepository.getOllamaBaseUrl().firstOrNull()
        if (url.isNullOrBlank()) return null
        return OllamaClient(httpClientFactory = httpClientFactory, baseUrl = url)
    }

    /**
     * Reads the local-only mode flag. Logs (at info level) when the gate
     * fires so the user can trace why a cloud client returned `null`.
     */
    private suspend fun isLocalOnlyMode(): Boolean {
        val blocked = settingsRepository.blockNetworkFromLocalModel.firstOrNull() ?: false
        if (blocked) Timber.i("KoogClientFactory: cloud provider gated by local-only mode")
        return blocked
    }
}
