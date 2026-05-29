package ai.agent.android.data.services.embedding

import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.services.EmbeddingException
import ai.agent.android.domain.services.EmbeddingProvider
import ai.koog.prompt.executor.ollama.client.OllamaModels
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-network [EmbeddingProvider] backed by an Ollama server running the
 * `nomic-embed-text` model (768-d), called through Koog's `OllamaClient`.
 *
 * This targets users who already run Ollama on their LAN (the same server the
 * app can use for chat completions) and want higher-quality embeddings than
 * the on-device USE model without sending data to a third-party cloud. It
 * reuses the Ollama base URL configured for chat in [ApiKeyRepository].
 *
 * When no base URL is configured it falls back to the on-device
 * [UseEmbeddingProvider]; the same dimension caveat as the cloud provider
 * applies (USE produces 512-d vectors, not 768-d).
 *
 * @property embedderFactory Builds the underlying Koog Ollama client.
 * @property apiKeyRepository Source of the Ollama base URL.
 * @property fallback On-device provider used when no base URL is configured.
 */
@Singleton
class OllamaEmbeddingProvider @Inject constructor(
    private val embedderFactory: KoogEmbedderFactory,
    private val apiKeyRepository: ApiKeyRepository,
    private val fallback: UseEmbeddingProvider,
) : EmbeddingProvider {

    override val id: String = EmbeddingProvider.ID_OLLAMA

    override val displayName: String = "Ollama (nomic-embed-text)"

    override val dimension: Int = DIMENSION

    override suspend fun embed(text: String): FloatArray = embed(listOf(text)).first()

    /**
     * Embeds [texts] via the Ollama server's batch embedding call, mapping each
     * returned `List<Double>` to a [FloatArray].
     *
     * Falls back to the on-device provider when no base URL is set; any
     * transport or server failure is wrapped in an [EmbeddingException].
     */
    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val baseUrl = apiKeyRepository.getOllamaBaseUrl().firstOrNull()
        if (baseUrl.isNullOrBlank()) {
            Timber.i("OllamaEmbeddingProvider: no base URL configured; using on-device fallback")
            return fallback.embed(texts)
        }

        val client = embedderFactory.ollamaClient(baseUrl)
        return runCatching {
            client.embed(texts, OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
                .map { it.toFloatVector() }
        }.getOrElse { throwable ->
            Timber.e(throwable, "Ollama embedding request failed")
            throw EmbeddingException("Ollama embedding request failed", throwable)
        }
    }

    private companion object {
        /** Output dimension of the `nomic-embed-text` Ollama model. */
        const val DIMENSION = 768
    }
}
