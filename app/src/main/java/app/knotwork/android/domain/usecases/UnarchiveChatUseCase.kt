package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Use case for restoring an archived chat session to the main thread list.
 *
 * The mirror image of [ArchiveChatUseCase]: it clears the same flag, and
 * because archiving never removed anything, the restored session comes back
 * with its full history, ordering and pipeline binding intact.
 *
 * Callers receive a [Result] so the UI can surface a failed write as a Snackbar
 * instead of an unhandled exception; the repository never throws across this
 * boundary.
 *
 * @property chatRepository Persistence sink for the session-level archive flag.
 */
class UnarchiveChatUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    /**
     * Restores the session identified by [sessionId] to the main thread list.
     *
     * Unarchiving a session that is not archived is a no-op that still reports
     * success — the caller asked for a state, not for a transition.
     *
     * @param sessionId Unique identifier of the session to restore. Blank
     *   values are rejected rather than silently matching zero rows.
     * @return [Result.success] once the flag is persisted, [Result.failure]
     *   for a blank id or a storage exception.
     */
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Session id cannot be blank"))
        }
        return try {
            chatRepository.setSessionArchived(sessionId, archived = false)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
