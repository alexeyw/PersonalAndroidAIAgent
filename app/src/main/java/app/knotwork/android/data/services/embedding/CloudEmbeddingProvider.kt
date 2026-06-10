package app.knotwork.android.data.services.embedding

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.services.EmbeddingException
import app.knotwork.android.domain.services.EmbeddingProvider
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cloud [EmbeddingProvider] backed by OpenAI `text-embedding-3-small` (1536-d),
 * called through Koog's `OpenAILLMClient`.
 *
 * Reusing the Koog client (the same transport `KoogClientFactory` uses for chat
 * completions) means no bespoke HTTP code and consistent auth/timeout handling.
 *
 * This provider never silently falls back to another backend: doing so would
 * break its [dimension] contract (a caller sizing buffers on `dimension = 1536`
 * must not receive a 512-d on-device vector). When no key is configured
 * [isAvailable] reports `false` so `EmbeddingProviderResolver` substitutes the
 * on-device default *before* this provider is ever returned; if [embed] is
 * nonetheless called without a key it fails loudly with an [EmbeddingException].
 *
 * @property embedderFactory Builds the underlying Koog embedding client.
 * @property apiKeyRepository Source of the OpenAI API key.
 */
@Singleton
class CloudEmbeddingProvider @Inject constructor(
    private val embedderFactory: KoogEmbedderFactory,
    private val apiKeyRepository: ApiKeyRepository,
) : EmbeddingProvider {

    override val id: String = EmbeddingProvider.ID_OPENAI_3_SMALL

    override val displayName: String = "OpenAI (text-embedding-3-small)"

    override val dimension: Int = DIMENSION

    /** Available only when a non-blank OpenAI API key is configured. */
    override suspend fun isAvailable(): Boolean = !apiKeyRepository.getOpenAIKey().firstOrNull().isNullOrBlank()

    override suspend fun embed(text: String): FloatArray = embed(listOf(text)).first()

    /**
     * Embeds [texts] via OpenAI's batch embeddings endpoint in a single
     * request, mapping each returned `List<Double>` to a [FloatArray].
     *
     * @throws EmbeddingException If no API key is configured, or the transport
     *   / API call fails. [CancellationException] is rethrown unchanged so
     *   coroutine cancellation is not swallowed.
     */
    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val key = apiKeyRepository.getOpenAIKey().firstOrNull()
        if (key.isNullOrBlank()) {
            throw EmbeddingException("OpenAI API key is not configured")
        }

        val client = embedderFactory.openAiClient(key)
        return try {
            client.embed(texts, OpenAIModels.Embeddings.TextEmbedding3Small)
                .map { it.toFloatVector() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "OpenAI embedding request failed")
            throw EmbeddingException("OpenAI embedding request failed", e)
        }
    }

    private companion object {
        /** Default output dimension of `text-embedding-3-small`. */
        const val DIMENSION = 1536
    }
}
