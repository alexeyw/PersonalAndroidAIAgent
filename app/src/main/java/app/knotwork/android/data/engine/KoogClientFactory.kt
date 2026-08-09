package app.knotwork.android.data.engine

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import app.knotwork.android.data.engine.retry.CloudRetryWrapper
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.retry.CloudRetryListener
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
    private val retryWrapper: CloudRetryWrapper,
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
     * Network deadlines applied to every cloud client.
     *
     * Koog's own default is 900 s for both the request and the socket, and the factory
     * never overrode it — so a provider that accepted the request and then went quiet
     * held the node for fifteen minutes. Measured, not assumed: a stalled stub was still
     * connected after 120 s under the shipped configuration, while an explicit config on
     * the same SSE path cut at its stated value.
     *
     * The load-bearing value is [SOCKET_TIMEOUT_MS], because it is applied **per read**:
     * it bounds how long the provider may stay *silent*, not how long a healthy answer
     * may take. That is deliberately the same rule the task queue's no-progress valve
     * uses — a long, steadily-streaming generation must never be cut for being long,
     * while a dead connection must not survive. [REQUEST_TIMEOUT_MS] is left at Koog's
     * generous value as a backstop for the pathological case of a provider that dribbles
     * bytes forever.
     */
    private val timeoutConfig = ConnectionTimeoutConfig(
        requestTimeoutMillis = REQUEST_TIMEOUT_MS,
        connectTimeoutMillis = CONNECT_TIMEOUT_MS,
        socketTimeoutMillis = SOCKET_TIMEOUT_MS,
    )

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
    override suspend fun createClient(provider: CloudProvider, retryListener: CloudRetryListener): Any? =
        rawClient(provider)?.let { raw ->
            retryWrapper.wrap(client = raw, provider = provider.id, listener = retryListener)
        }

    /**
     * Builds the raw, un-decorated Koog client for [provider], applying the
     * local-only gate and the per-provider credential checks. Retry wrapping is
     * applied separately by [createClient] / the public helpers.
     *
     * @return The raw client, or `null` when credentials are missing or
     *   local-only mode is on.
     */
    private suspend fun rawClient(provider: CloudProvider): LLMClient? = when (provider) {
        CloudProvider.OPENAI -> rawOpenAI()
        CloudProvider.ANTHROPIC -> rawAnthropic()
        CloudProvider.GOOGLE -> rawGoogle()
        CloudProvider.DEEPSEEK -> rawDeepSeek()
        CloudProvider.OLLAMA -> rawOllama()
    }

    /**
     * Creates a retry-wrapped OpenAI LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createOpenAIExecutor(): LLMClient? = rawOpenAI()?.let { wrapNoObserve(it, CloudProvider.OPENAI) }

    /**
     * Creates a retry-wrapped Anthropic LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createAnthropicExecutor(): LLMClient? =
        rawAnthropic()?.let { wrapNoObserve(it, CloudProvider.ANTHROPIC) }

    /**
     * Creates a retry-wrapped Google (Gemini) LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createGoogleExecutor(): LLMClient? = rawGoogle()?.let { wrapNoObserve(it, CloudProvider.GOOGLE) }

    /**
     * Creates a retry-wrapped DeepSeek LLMClient.
     * @return The client, or null if the API key is not configured or
     *   local-only mode is on.
     */
    suspend fun createDeepSeekExecutor(): LLMClient? = rawDeepSeek()?.let { wrapNoObserve(it, CloudProvider.DEEPSEEK) }

    /**
     * Creates a retry-wrapped Ollama LLMClient connected to the configured local server.
     * @return The client, or null if the custom URL is not configured.
     */
    suspend fun createOllamaExecutor(): LLMClient? = rawOllama()?.let { wrapNoObserve(it, CloudProvider.OLLAMA) }

    /** Wraps a raw client with the retry policy but no retry observation (off-graph callers). */
    private suspend fun wrapNoObserve(client: LLMClient, provider: CloudProvider): LLMClient =
        retryWrapper.wrap(client = client, provider = provider.id)

    private suspend fun rawOpenAI(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getOpenAIKey().firstOrNull()?.trim()
        if (key.isNullOrBlank()) return null
        return OpenAILLMClient(
            apiKey = key,
            settings = OpenAIClientSettings(timeoutConfig = timeoutConfig),
            httpClientFactory = httpClientFactory,
        )
    }

    private suspend fun rawAnthropic(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getAnthropicKey().firstOrNull()?.trim()
        if (key.isNullOrBlank()) return null
        return AnthropicLLMClient(
            apiKey = key,
            settings = AnthropicClientSettings(timeoutConfig = timeoutConfig),
            httpClientFactory = httpClientFactory,
        )
    }

    private suspend fun rawGoogle(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getGoogleKey().firstOrNull()?.trim()
        if (key.isNullOrBlank()) return null
        return GoogleLLMClient(
            apiKey = key,
            settings = GoogleClientSettings(timeoutConfig = timeoutConfig),
            httpClientFactory = httpClientFactory,
        )
    }

    private suspend fun rawDeepSeek(): LLMClient? {
        if (isLocalOnlyMode()) return null
        val key = apiKeyRepository.getDeepSeekKey().firstOrNull()?.trim()
        if (key.isNullOrBlank()) return null
        return DeepSeekLLMClient(
            apiKey = key,
            settings = DeepSeekClientSettings(timeoutConfig = timeoutConfig),
            httpClientFactory = httpClientFactory,
        )
    }

    private suspend fun rawOllama(): LLMClient? {
        val url = apiKeyRepository.getOllamaBaseUrl().firstOrNull()?.trim()
        if (url.isNullOrBlank()) return null
        return OllamaClient(
            httpClientFactory = httpClientFactory,
            baseUrl = url,
            timeoutConfig = timeoutConfig,
        )
    }

    /**
     * Reads the local-only mode flag. Logs (at info level) when the gate
     * fires so the user can trace why a cloud client returned `null`.
     */
    private companion object {
        /**
         * Longest the provider may stay silent between bytes. Applied per read, so a
         * healthy long generation is untouched and a dead stream is not.
         */
        const val SOCKET_TIMEOUT_MS: Long = 60_000

        /** Connection establishment budget — a person is usually waiting on this. */
        const val CONNECT_TIMEOUT_MS: Long = 30_000

        /** Outer backstop for a request that never ends despite continuous bytes. */
        const val REQUEST_TIMEOUT_MS: Long = 900_000
    }

    private suspend fun isLocalOnlyMode(): Boolean {
        val blocked = settingsRepository.blockNetworkFromLocalModel.firstOrNull() ?: false
        if (blocked) Timber.i("KoogClientFactory: cloud provider gated by local-only mode")
        return blocked
    }
}
