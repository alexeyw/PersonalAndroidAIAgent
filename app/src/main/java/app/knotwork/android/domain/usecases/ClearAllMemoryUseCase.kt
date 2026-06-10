package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.services.MemorySearchStatsTracker
import javax.inject.Inject

/**
 * Wipes every memory chunk — including pinned entries — from the
 * underlying table, and resets the session similarity-score statistics
 * (scores observed against the deleted corpus are no longer
 * representative). Backs the Settings → Memory → Clear destructive
 * action.
 *
 * The use case is unconditional: callers (specifically `SettingsViewModel`)
 * MUST stage a typed-confirm dialog before invoking it. The contract
 * intentionally does not return a count or a result; the only relevant
 * post-condition is "the table is empty", which the live `observeStats`
 * stream reflects on the very next emission.
 */
class ClearAllMemoryUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memorySearchStatsTracker: MemorySearchStatsTracker,
) {
    /** Deletes every stored memory chunk and resets the AVG SCORE window. Irreversible. */
    suspend operator fun invoke() {
        memoryRepository.deleteAllMemories()
        memorySearchStatsTracker.reset()
    }
}
