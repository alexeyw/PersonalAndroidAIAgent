package ai.agent.android.domain.usecases

import ai.agent.android.domain.memoryio.MemoryJsonSerializer
import ai.agent.android.domain.models.MemoryExportDocument
import ai.agent.android.domain.models.MemoryImportOutcome
import ai.agent.android.domain.models.MemoryImportStrategy
import ai.agent.android.domain.repositories.MemoryRepository
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
 *   destructive clear used by [MemoryImportStrategy.Replace], and the bulk
 *   insert.
 */
class MemoryImportUseCase @Inject constructor(private val memoryRepository: MemoryRepository) {

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
     * - [MemoryImportStrategy.Replace] wipes every existing chunk first, so the
     *   store becomes an exact copy of the file.
     * - [MemoryImportStrategy.Merge] keeps existing chunks and inserts only
     *   those whose id is not already present (duplicates are skipped).
     *
     * When [document]'s `embeddingProviderId` differs from [activeProviderId]
     * the imported vectors live in an incompatible space, so every inserted
     * chunk is flagged for lazy re-embedding on the next retrieval.
     *
     * @param document The parsed document to import.
     * @param strategy How to reconcile with the existing store.
     * @param activeProviderId Id of the importing device's active embedding
     *   provider.
     * @return A [MemoryImportResult] describing how many chunks were imported
     *   and skipped, and whether re-embedding is pending.
     */
    suspend fun import(
        document: MemoryExportDocument,
        strategy: MemoryImportStrategy,
        activeProviderId: String,
    ): MemoryImportResult {
        val needsReembedding = document.embeddingProviderId != activeProviderId
        val toInsert = when (strategy) {
            MemoryImportStrategy.Replace -> {
                memoryRepository.deleteAllMemories()
                document.chunks
            }
            MemoryImportStrategy.Merge -> {
                val existing = memoryRepository.getExistingMemoryIds()
                document.chunks.filter { it.id !in existing }
            }
        }
        memoryRepository.insertImportedMemories(toInsert, needsReembedding)
        return MemoryImportResult(
            imported = toInsert.size,
            skipped = document.chunks.size - toInsert.size,
            needsReembedding = needsReembedding,
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
