package ai.agent.android.data.services.embedding

import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.services.EmbeddingException
import ai.agent.android.domain.services.EmbeddingProvider
import ai.koog.prompt.executor.ollama.client.OllamaModels
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local-network [EmbeddingProvider] backed by an Ollama server running the
 * `nomic-embed-text` model (768-d), called through Koog's `OllamaClient`.
 *
 * This targets users who already run Ollama on their LAN (the same server the
 * app can use for chat completions) and want higher-quality embeddings than
 * the on-device USE model without sending data to a third-party cloud. It
 * reuses the Ollama base URL configured for chat in [ApiKeyRepository].
 *
 * Like the cloud provider, it never silently falls back to another backend
 * (that would break its [dimension] contract): when no base URL is configured
 * [isAvailable] reports `false` so `EmbeddingProviderResolver` substitutes the
 * on-device default; a key-less [embed] call fails with an [EmbeddingException].
 *
 * @property embedderFactory Builds the underlying Koog Ollama client.
 * @property apiKeyRepository Source of the Ollama base URL.
 */
@Singleton
class OllamaEmbeddingProvider @Inject constructor(
    private val embedderFactory: KoogEmbedderFactory,
    private val apiKeyRepository: ApiKeyRepository,
) : EmbeddingProvider {

    override val id: String = EmbeddingProvider.ID_OLLAMA

    override val displayName: String = "Ollama (nomic-embed-text)"

    override val dimension: Int = DIMENSION

    /** Available only when a non-blank Ollama base URL is configured. */
    override suspend fun isAvailable(): Boolean = !apiKeyRepository.getOllamaBaseUrl().firstOrNull().isNullOrBlank()

    override suspend fun embed(text: String): FloatArray = embed(listOf(text)).first()

    /**
     * Embeds [texts] via the Ollama server's batch embedding call, mapping each
     * returned `List<Double>` to a [FloatArray].
     *
     * @throws EmbeddingException If no base URL is configured, or the transport
     *   / server call fails. [CancellationException] is rethrown unchanged so
     *   coroutine cancellation is not swallowed.
     */
    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val baseUrl = apiKeyRepository.getOllamaBaseUrl().firstOrNull()
        if (baseUrl.isNullOrBlank()) {
            throw EmbeddingException("Ollama base URL is not configured")
        }

        val client = embedderFactory.ollamaClient(baseUrl)
        return runCatching {
            client.embed(texts, OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
                .map { it.toFloatVector() }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Timber.e(throwable, "Ollama embedding request failed")
            throw EmbeddingException("Ollama embedding request failed", throwable)
        }
    }

    private companion object {
        /** Output dimension of the `nomic-embed-text` Ollama model. */
        const val DIMENSION = 768
    }
}
