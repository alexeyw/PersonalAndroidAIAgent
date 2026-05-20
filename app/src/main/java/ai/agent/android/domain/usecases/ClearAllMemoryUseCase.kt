package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.MemoryRepository
import javax.inject.Inject

/**
 * Wipes every memory chunk — including pinned entries — from the
 * underlying table. Backs the Settings → Memory → Clear destructive
 * action.
 *
 * The use case is unconditional: callers (specifically `SettingsViewModel`)
 * MUST stage a typed-confirm dialog before invoking it. The contract
 * intentionally does not return a count or a result; the only relevant
 * post-condition is "the table is empty", which the live `observeStats`
 * stream reflects on the very next emission.
 */
class ClearAllMemoryUseCase @Inject constructor(private val memoryRepository: MemoryRepository) {
    /** Deletes every stored memory chunk. Irreversible. */
    suspend operator fun invoke() {
        memoryRepository.deleteAllMemories()
    }
}
