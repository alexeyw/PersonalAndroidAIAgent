package app.knotwork.android.domain.models

/**
 * Result of an [app.knotwork.android.domain.services.AgentWorkspace] operation:
 * either a [Success] carrying the produced value or a [Failure] carrying a
 * typed [WorkspaceError].
 *
 * A dedicated sealed type is used instead of [kotlin.Result] because the failure
 * cause is a closed, exhaustively-matchable [WorkspaceError] domain — not an
 * open [Throwable]. Callers `when`-match the error and never have to reason about
 * arbitrary exception classes.
 *
 * @param T The type of the value produced on success.
 */
sealed interface WorkspaceResult<out T> {
    /**
     * The operation completed successfully.
     *
     * @property value The produced value.
     */
    data class Success<out T>(val value: T) : WorkspaceResult<T>

    /**
     * The operation was refused or failed.
     *
     * @property error The typed cause of the failure.
     */
    data class Failure(val error: WorkspaceError) : WorkspaceResult<Nothing>
}
