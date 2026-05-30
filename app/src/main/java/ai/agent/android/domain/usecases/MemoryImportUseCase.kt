package ai.agent.android.domain.usecases

import ai.agent.android.domain.memoryio.MemoryJsonSerializer
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
                memoryRepository.replaceImportedMemories(document.chunks, needsReembedding)
                document.chunks
            }
            MemoryImportStrategy.Merge -> {
                val existing = memoryRepository.getExistingMemoryIds()
                val fresh = document.chunks.filter { it.id == 0L || it.id !in existing }
                memoryRepository.insertImportedMemories(fresh, needsReembedding)
                fresh
            }
        }
        // Schedule the background repair only when chunks landed under a
        // mismatched provider; nothing to do otherwise.
        if (needsReembedding && toInsert.isNotEmpty()) {
            reembedScheduler.schedule()
        }
        return MemoryImportResult(
            imported = toInsert.size,
            skipped = document.chunks.size - toInsert.size,
            needsReembedding = needsReembedding && toInsert.isNotEmpty(),
        )
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
