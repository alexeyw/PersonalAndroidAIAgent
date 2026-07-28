package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Use case for archiving a chat session.
 *
 * Archiving is non-destructive: the session row, its messages, its trace steps
 * and its pipeline-run records are all kept, and the only observable effect is
 * that the conversation leaves the main thread list and appears in the archive
 * instead. [UnarchiveChatUseCase] reverses it with nothing lost.
 *
 * New activity in an archived session (a background trigger run, a scheduled
 * task) deliberately does **not** un-archive it — the flag is a user decision
 * and only the user reverses it.
 *
 * Callers receive a [Result] so the UI can surface a failed write as a Snackbar
 * instead of an unhandled exception; the repository never throws across this
 * boundary.
 *
 * @property chatRepository Persistence sink for the session-level archive flag.
 */
class ArchiveChatUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    /**
     * Archives the session identified by [sessionId].
     *
     * Archiving an already-archived session is a no-op that still reports
     * success — the caller asked for a state, not for a transition, so a
     * double tap (or a replayed action after process death) must not fail.
     *
     * @param sessionId Unique identifier of the session to archive. Blank
     *   values are rejected rather than silently matching zero rows, so a
     *   caller wiring up an empty id learns about it.
     * @return [Result.success] once the flag is persisted, [Result.failure]
     *   for a blank id or a storage exception.
     */
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Session id cannot be blank"))
        }
        return try {
            chatRepository.setSessionArchived(sessionId, archived = true)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
