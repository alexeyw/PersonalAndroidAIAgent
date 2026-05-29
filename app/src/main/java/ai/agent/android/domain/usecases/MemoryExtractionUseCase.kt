package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import javax.inject.Inject

/**
 * Distils durable, long-term facts out of a finished conversation and persists
 * the novel ones into long-term memory.
 *
 * Lifecycle of one extraction pass:
 *  1. Take the most recent slice of the dialogue (see [RECENT_MESSAGE_WINDOW]).
 *  2. Render the conservative extraction system prompt
 *     ([DefaultPrompts.MemoryExtraction.SYSTEM_FALLBACK]) — resolving `$DATE`
 *     for temporal grounding — and run it once through the local LiteRT model.
 *  3. Parse the model's reply as a JSON array of `{type, text}` facts.
 *  4. For each fact, compute its embedding with the **active** provider and
 *     reject near-duplicates (cosine similarity ≥ [DEDUP_SIMILARITY_THRESHOLD])
 *     of either an already-stored chunk or a fact accepted earlier in the same
 *     pass.
 *  5. Save the survivors tagged with [MemorySource.ChatSession].
 *
 * The use case is intentionally free of the auto-extract feature toggle: the
 * trigger that calls it owns that gate, leaving this use case reusable by the
 * manual "Save to memory" path (Phase 25 / Task 7). It never throws for an
 * empty / malformed model reply or a model that cannot be loaded — it returns a
 * zero-result [MemoryExtractionOutcome] instead, so a best-effort background
 * pass can never break a conversation.
 *
 * @property llmInferenceEngine Local model used to run the extraction prompt.
 * @property loadModelUseCase Ensures the active model is loaded before inference.
 * @property promptTemplateEngine Substitutes runtime `$VARIABLE`s (here `$DATE`).
 * @property promptVariableProviders Registered providers backing the templating.
 * @property embeddingProviderResolver Resolves the active embedding backend per call.
 * @property memoryRepository Persistence + similarity search over stored chunks.
 * @property settingsRepository Source of the similarity-search pool size.
 */
class MemoryExtractionUseCase @Inject constructor(
    private val llmInferenceEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Runs one extraction pass over [messages] and persists the novel facts.
     *
     * @param sessionId Id of the chat session the [messages] belong to; recorded
     *   as [MemorySource.ChatSession] on every saved chunk.
     * @param messages The conversation to mine. Only the trailing
     *   [RECENT_MESSAGE_WINDOW] entries are considered; passes with fewer than
     *   [MIN_MESSAGES_TO_EXTRACT] messages are skipped (too little signal).
     * @return A summary of how many facts were parsed, saved, and skipped as
     *   duplicates.
     */
    suspend operator fun invoke(sessionId: String, messages: List<ChatMessage>): MemoryExtractionOutcome =
        withContext(Dispatchers.Default) {
            val recent = messages.takeLast(RECENT_MESSAGE_WINDOW)
            if (recent.size < MIN_MESSAGES_TO_EXTRACT) {
                return@withContext MemoryExtractionOutcome.EMPTY
            }

            // Ensure the active model is loaded. The model was almost certainly
            // just used by the completing pipeline, so this is usually a no-op;
            // if it cannot be loaded we skip rather than fail the background pass.
            if (loadModelUseCase() is Result.Error) {
                Timber.tag(TAG).w("Active model unavailable; skipping memory extraction")
                return@withContext MemoryExtractionOutcome.EMPTY
            }

            val rawReply = runInference(recent)
            val facts = parseFacts(rawReply)
            if (facts.isEmpty()) {
                return@withContext MemoryExtractionOutcome(parsed = 0, saved = 0, skippedDuplicates = 0)
            }

            persistNovelFacts(sessionId, facts)
        }

    /**
     * Builds the full extraction prompt from the rendered system prompt and the
     * role-labelled dialogue, then runs it through the local model and joins the
     * streamed tokens into a single reply.
     *
     * @param messages Trailing slice of the conversation to embed in the prompt.
     * @return The model's raw textual reply (expected to be a JSON array).
     */
    private suspend fun runInference(messages: List<ChatMessage>): String {
        val systemPrompt = promptTemplateEngine.render(
            DefaultPrompts.MemoryExtraction.SYSTEM_FALLBACK,
            promptVariableProviders.toList(),
        )
        val dialogue = messages.joinToString(separator = "\n") { message ->
            "${message.role.label()}: ${message.content}"
        }
        val fullPrompt = "$systemPrompt\n\nCONVERSATION:\n$dialogue\n\nJSON OUTPUT: "

        return try {
            llmInferenceEngine.generateResponseStream(fullPrompt).toList().joinToString(separator = "")
        } catch (e: CancellationException) {
            // Preserve structured-concurrency cancellation: a broad catch would
            // otherwise let the calling scope believe the pass finished cleanly.
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Memory extraction inference failed")
            ""
        }
    }

    /**
     * Embeds and stores every fact that is not a near-duplicate of an existing
     * chunk or of a fact already accepted in this pass.
     *
     * @param sessionId Session recorded as the provenance of each saved chunk.
     * @param facts Parsed candidate facts.
     * @return Outcome counters for the pass.
     */
    private suspend fun persistNovelFacts(sessionId: String, facts: List<String>): MemoryExtractionOutcome {
        val searchPoolLimit = settingsRepository.maxMemoryChunksForSearch.first()
        val provider = embeddingProviderResolver.resolve()

        // Embed every fact in a single batch call. Cloud providers turn this
        // into one network round-trip instead of N (one per fact); on-device
        // providers map over their single-text path. The result is
        // index-aligned with [facts].
        val embeddings = try {
            provider.embed(facts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to embed extracted facts; skipping the pass")
            return MemoryExtractionOutcome(parsed = facts.size, saved = 0, skippedDuplicates = 0)
        }
        if (embeddings.size != facts.size) {
            // Defensive: a provider that breaks the index-alignment contract
            // would mis-attribute embeddings to facts — drop the pass rather
            // than persist scrambled vectors.
            Timber.tag(TAG).w(
                "Embedding count %d != fact count %d; skipping the pass",
                embeddings.size,
                facts.size,
            )
            return MemoryExtractionOutcome(parsed = facts.size, saved = 0, skippedDuplicates = 0)
        }

        val source = MemorySource.ChatSession(sessionId)
        val acceptedEmbeddings = mutableListOf<FloatArray>()
        var saved = 0
        var skipped = 0

        for (i in facts.indices) {
            val embedding = embeddings[i]
            if (isDuplicate(embedding, searchPoolLimit, acceptedEmbeddings)) {
                skipped++
                continue
            }

            memoryRepository.saveMemory(text = facts[i], embedding = embedding, source = source)
            acceptedEmbeddings += embedding
            saved++
        }

        return MemoryExtractionOutcome(parsed = facts.size, saved = saved, skippedDuplicates = skipped)
    }

    /**
     * Decides whether [embedding] is a near-duplicate. A fact is a duplicate
     * when its cosine similarity to either a stored chunk or a fact already
     * accepted in this pass is at least [DEDUP_SIMILARITY_THRESHOLD].
     *
     * @param embedding Embedding of the candidate fact.
     * @param searchPoolLimit Number of recent chunks to scan for the stored-chunk check.
     * @param acceptedEmbeddings Embeddings accepted earlier in the same pass.
     * @return `true` if the candidate should be skipped as a duplicate.
     */
    private suspend fun isDuplicate(
        embedding: FloatArray,
        searchPoolLimit: Int,
        acceptedEmbeddings: List<FloatArray>,
    ): Boolean {
        val topStored = memoryRepository
            .findSimilarMemories(embedding, searchPoolLimit, limit = 1)
            .firstOrNull()
            ?.second
            ?: 0f
        if (topStored >= DEDUP_SIMILARITY_THRESHOLD) return true
        return acceptedEmbeddings.any { cosineSimilarity(embedding, it) >= DEDUP_SIMILARITY_THRESHOLD }
    }

    /**
     * Parses the model reply into a list of fact texts.
     *
     * Accepts a bare JSON array or one fenced in a ```json block, mirroring the
     * defensive parsing the orchestrator applies to LLM list output. Only
     * elements with a non-blank `text` and a recognised `type`
     * ([VALID_FACT_TYPES]) are kept; everything else is dropped. Never throws —
     * a malformed reply yields an empty list.
     *
     * @param reply Raw model output.
     * @return The extracted fact texts (possibly empty).
     */
    private fun parseFacts(reply: String): List<String> {
        val jsonArrayText = extractJsonArray(reply) ?: return emptyList()
        return try {
            val array = JSONArray(jsonArrayText)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val type = obj.optString(KEY_TYPE).trim().lowercase()
                    val text = obj.optString(KEY_TEXT).trim()
                    if (text.isNotEmpty() && type in VALID_FACT_TYPES) {
                        add(text)
                    }
                }
            }
        } catch (e: JSONException) {
            Timber.tag(TAG).w(e, "Failed to parse extracted facts JSON")
            emptyList()
        }
    }

    /**
     * Isolates the JSON array substring from a model reply, tolerating
     * surrounding prose or a ```json fence.
     *
     * @param reply Raw model output.
     * @return The `[...]` substring, or `null` if none is present.
     */
    private fun extractJsonArray(reply: String): String? {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return reply.substring(start, end + 1)
    }

    /**
     * Cosine similarity between two equal-length vectors, used for the
     * within-pass duplicate check. Returns `0` for mismatched or empty vectors
     * or a zero-magnitude operand. Mirrors the data-layer implementation so the
     * stored-chunk and within-pass checks use the same metric.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    /** Maps a [Role] to the speaker label used inside the extraction prompt. */
    private fun Role.label(): String = when (this) {
        Role.USER -> "User"
        Role.AGENT -> "Assistant"
        Role.SYSTEM -> "System"
    }

    private companion object {
        const val TAG = "MemoryExtraction"

        /** Maximum number of trailing messages fed to the extractor. */
        const val RECENT_MESSAGE_WINDOW = 20

        /** Minimum messages required before a pass is worthwhile. */
        const val MIN_MESSAGES_TO_EXTRACT = 2

        /**
         * Cosine-similarity at or above which a candidate fact is treated as a
         * duplicate of an existing chunk and skipped. Fixed (not user-tunable):
         * high enough to drop paraphrases of the same fact while letting
         * genuinely new facts through.
         */
        const val DEDUP_SIMILARITY_THRESHOLD = 0.92f

        const val KEY_TYPE = "type"
        const val KEY_TEXT = "text"

        /** Recognised fact categories emitted by the extraction prompt. */
        val VALID_FACT_TYPES = setOf("preference", "event", "relation")
    }

    /**
     * Summary of a single extraction pass.
     *
     * @property parsed Number of well-formed facts parsed from the model reply.
     * @property saved Number of novel facts persisted to memory.
     * @property skippedDuplicates Number of facts dropped as near-duplicates.
     */
    data class MemoryExtractionOutcome(val parsed: Int, val saved: Int, val skippedDuplicates: Int) {
        /** Shared constants for [MemoryExtractionOutcome]. */
        companion object {
            /** Result of a pass that did nothing (skipped, empty, or model unavailable). */
            val EMPTY = MemoryExtractionOutcome(parsed = 0, saved = 0, skippedDuplicates = 0)
        }
    }
}
