package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Persists a single piece of text into long-term memory as a deliberate,
 * user-attributable write.
 *
 * This is the direct-wrapper path behind the chat "Save to memory" action: it
 * embeds the supplied text with the user's active embedding provider (resolved
 * by [EmbeddingProviderResolver], so the chunk lands in the same vector space
 * as the retrieval query) and stores it tagged [MemorySource.Manual]. Unlike
 * [MemoryExtractionUseCase], it neither runs a local-model distillation pass
 * nor requires a minimum message count — the user picked exactly this message,
 * so it is saved verbatim.
 *
 * The operation is best-effort: a blank input is skipped, and an embedding
 * failure (e.g. a transient network error from a cloud provider) is swallowed
 * into [SaveToMemoryOutcome.Failed] so the caller can surface a snackbar
 * instead of crashing. [CancellationException] is rethrown to preserve
 * structured concurrency.
 *
 * @property embeddingProviderResolver Resolves the active embedding provider.
 * @property memoryRepository Persistence gateway for memory chunks.
 */
class SaveMessageToMemoryUseCase @Inject constructor(
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val memoryRepository: MemoryRepository,
) {
    /**
     * Embeds [text] and saves it as a [MemorySource.Manual] chunk.
     *
     * @param text The raw message text chosen by the user.
     * @return [SaveToMemoryOutcome.Skipped] when [text] is blank after
     *   trimming, [SaveToMemoryOutcome.Saved] with the new chunk id on success,
     *   or [SaveToMemoryOutcome.Failed] when embedding or persistence throws.
     */
    suspend operator fun invoke(text: String): SaveToMemoryOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SaveToMemoryOutcome.Skipped
        return try {
            val embedding = embeddingProviderResolver.resolve().embed(trimmed)
            val id = memoryRepository.saveMemory(
                text = trimmed,
                embedding = embedding,
                source = MemorySource.Manual,
            )
            SaveToMemoryOutcome.Saved(id = id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to save message to memory")
            SaveToMemoryOutcome.Failed(cause = e)
        }
    }
}

/**
 * Result of a manual "Save to memory" attempt.
 */
sealed interface SaveToMemoryOutcome {
    /** The text was embedded and persisted; carries the new chunk [id]. */
    data class Saved(val id: Long) : SaveToMemoryOutcome

    /** The input was blank after trimming; nothing was saved. */
    data object Skipped : SaveToMemoryOutcome

    /** Embedding or persistence failed; carries the [cause] for logging. */
    data class Failed(val cause: Throwable) : SaveToMemoryOutcome
}
