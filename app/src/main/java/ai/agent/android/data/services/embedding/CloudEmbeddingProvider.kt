package ai.agent.android.data.services.embedding

import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.services.EmbeddingException
import ai.agent.android.domain.services.EmbeddingProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud [EmbeddingProvider] backed by OpenAI `text-embedding-3-small` (1536-d),
 * called through Koog's `OpenAILLMClient`.
 *
 * Reusing the Koog client (the same transport `KoogClientFactory` uses for chat
 * completions) means no bespoke HTTP code and consistent auth/timeout handling.
 * When no OpenAI key is configured, this provider transparently falls back to
 * the on-device [UseEmbeddingProvider] so memory operations keep working
 * offline — note the resulting vectors then have the USE dimension (512), not
 * 1536; reconciling stored vectors after a provider switch is handled by the
 * later re-embedding tasks of this phase.
 *
 * @property embedderFactory Builds the underlying Koog embedding client.
 * @property apiKeyRepository Source of the OpenAI API key.
 * @property fallback On-device provider used when no key is configured.
 */
@Singleton
class CloudEmbeddingProvider @Inject constructor(
    private val embedderFactory: KoogEmbedderFactory,
    private val apiKeyRepository: ApiKeyRepository,
    private val fallback: UseEmbeddingProvider,
) : EmbeddingProvider {

    override val id: String = EmbeddingProvider.ID_OPENAI_3_SMALL

    override val displayName: String = "OpenAI (text-embedding-3-small)"

    override val dimension: Int = DIMENSION

    override suspend fun embed(text: String): FloatArray = embed(listOf(text)).first()

    /**
     * Embeds [texts] via OpenAI's batch embeddings endpoint in a single
     * request, mapping each returned `List<Double>` to a [FloatArray].
     *
     * Falls back to the on-device provider when no API key is set. Any
     * transport or API failure is wrapped in an [EmbeddingException] rather
     * than being swallowed, so callers can distinguish a genuine failure from
     * the offline-fallback case.
     */
    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val key = apiKeyRepository.getOpenAIKey().firstOrNull()
        if (key.isNullOrBlank()) {
            Timber.i("CloudEmbeddingProvider: no OpenAI key configured; using on-device fallback")
            return fallback.embed(texts)
        }

        val client = embedderFactory.openAiClient(key)
        return runCatching {
            client.embed(texts, OpenAIModels.Embeddings.TextEmbedding3Small)
                .map { it.toFloatVector() }
        }.getOrElse { throwable ->
            Timber.e(throwable, "OpenAI embedding request failed")
            throw EmbeddingException("OpenAI embedding request failed", throwable)
        }
    }

    private companion object {
        /** Default output dimension of `text-embedding-3-small`. */
        const val DIMENSION = 1536
    }
}
