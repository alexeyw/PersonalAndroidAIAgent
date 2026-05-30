package ai.agent.android.domain.usecases

import ai.agent.android.domain.memoryio.MemoryJsonSerializer
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemoryExportDocument
import ai.agent.android.domain.models.MemoryImportOutcome
import ai.agent.android.domain.models.MemoryImportStrategy
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.MemoryReembedScheduler
import javax.inject.Inject

/**
 * Imports long-term memory chunks from a JSON document produced by
 * [ai.agent.android.domain.usecases.ExportMemoryBaseUseCase] on another device.
 *
 * Two-step orchestration, mirroring [ImportPipelineUseCase]:
 *
 * 1. [parse] turns the raw JSON into a [MemoryImportOutcome] so the UI can
 *    branch (Success / SchemaMismatch / Failure) and surface a warning before
 *    touching the database.
 * 2. [import] reconciles the parsed [MemoryExportDocument] with the local store
 *    under the chosen [MemoryImportStrategy] and reports what happened.
 *
 * Splitting parse from import keeps the use case testable without an `Activity`
 * and lets the UI raise a strategy / compatibility dialog before any mutation.
 *
 * @property memoryRepository Backing store for the existing-id lookup, the
 *   transactional replace used by [MemoryImportStrategy.Replace], and the bulk
 *   insert.
 * @property reembedScheduler Enqueues the background re-embed pass when the
 *   imported chunks were flagged for re-embedding (provider mismatch).
 */
class MemoryImportUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val reembedScheduler: MemoryReembedScheduler,
) {

    /**
     * Parses [jsonText] into a [MemoryImportOutcome] without persisting
     * anything. Never throws — see [MemoryJsonSerializer.parse].
     *
     * @param jsonText Raw JSON read from the user-selected file.
     * @return The parse outcome the UI should branch on.
     */
    fun parse(jsonText: String): MemoryImportOutcome = MemoryJsonSerializer.parse(jsonText)

    /**
     * Reconciles [document] with the local store under [strategy].
     *
     * - [MemoryImportStrategy.Replace] atomically wipes every existing chunk and
     *   loads the file's chunks, so the store becomes an exact copy of the file.
     *   A document with no chunks is a no-op: the existing store is left intact
     *   rather than wiped with nothing to load.
     * - [MemoryImportStrategy.Merge] keeps existing chunks and inserts only
     *   those whose id is not already present (duplicates are skipped).
     *
     * When [document]'s `embeddingProviderId` differs from [activeProviderId]
     * the imported vectors live in an incompatible space, so every inserted
     * chunk is flagged for re-embedding and a background re-embed pass is
     * scheduled (the worker repairs them off the hot path).
     *
     * @param document The parsed document to import.
     * @param strategy How to reconcile with the existing store.
     * @param activeProviderId Id of the importing device's active embedding
     *   provider.
     * @return A [MemoryImportResult] describing how many chunks were imported
     *   and skipped, and whether a re-embed was scheduled.
     */
    suspend fun import(
        document: MemoryExportDocument,
        strategy: MemoryImportStrategy,
        activeProviderId: String,
    ): MemoryImportResult {
        val needsReembedding = document.embeddingProviderId != activeProviderId
        val toInsert = when (strategy) {
            MemoryImportStrategy.Replace -> {
                // Guard: never wipe the store when there is nothing to load.
                if (document.chunks.isEmpty()) {
                    return MemoryImportResult(imported = 0, skipped = 0, needsReembedding = false)
                }
                val deduped = document.chunks.dedupById()
                memoryRepository.replaceImportedMemories(deduped, needsReembedding)
                deduped
            }
            MemoryImportStrategy.Merge -> {
                val existing = memoryRepository.getExistingMemoryIds()
                // Drop chunks whose id is already stored. id == 0 always passes
                // (existing holds only real positive row ids) and dedupById then
                // keeps every id-0 chunk while collapsing positive-id duplicates
                // within the file, so the count matches what REPLACE-on-conflict
                // actually persists.
                val fresh = document.chunks.filter { it.id !in existing }.dedupById()
                memoryRepository.insertImportedMemories(fresh, needsReembedding)
                fresh
            }
        }
        // Schedule the background repair only when chunks landed under a
        // mismatched provider; nothing to do otherwise.
        val scheduledReembed = needsReembedding && toInsert.isNotEmpty()
        if (scheduledReembed) {
            reembedScheduler.schedule()
        }
        return MemoryImportResult(
            imported = toInsert.size,
            skipped = document.chunks.size - toInsert.size,
            needsReembedding = scheduledReembed,
        )
    }

    /**
     * Drops file-internal duplicate ids so the reported `imported` count matches
     * the rows actually persisted: `@Insert(onConflict = REPLACE)` collapses two
     * chunks sharing a positive id into a single row (last wins). Chunks with
     * `id == 0` are never deduped — each gets its own auto-assigned key.
     */
    private fun List<MemoryChunk>.dedupById(): List<MemoryChunk> {
        val byId = LinkedHashMap<Long, MemoryChunk>()
        val autoAssigned = ArrayList<MemoryChunk>()
        for (chunk in this) {
            if (chunk.id == 0L) autoAssigned.add(chunk) else byId[chunk.id] = chunk
        }
        return byId.values + autoAssigned
    }
}

/**
 * Summary of a completed [MemoryImportUseCase.import] call, used to compose the
 * confirmation snackbar.
 *
 * @property imported Number of chunks written to the store.
 * @property skipped Number of chunks dropped as duplicates (always `0` for
 *   [MemoryImportStrategy.Replace]).
 * @property needsReembedding `true` when the imported chunks were flagged for
 *   lazy re-embedding because the file used a different embedding provider.
 */
data class MemoryImportResult(val imported: Int, val skipped: Int, val needsReembedding: Boolean)
