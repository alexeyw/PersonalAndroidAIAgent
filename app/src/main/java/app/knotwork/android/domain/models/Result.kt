package app.knotwork.android.domain.models

/**
 * A generic class that holds a value with its success status or an error.
 *
 * This sealed interface is used to pass data between layers (especially from Data to Domain
 * and Domain to Presentation) in a safe manner, forcing the caller to handle both the
 * success and error cases explicitly without relying on exception handling.
 *
 * @param D The type of the data on success.
 * @param E The type of the error, which must implement [AppError].
 */
sealed interface Result<out D, out E : AppError> {

    /**
     * Represents a successful outcome containing the requested data.
     *
     * @property data The payload returned upon success.
     */
    data class Success<out D, out E : AppError>(val data: D) : Result<D, E>

    /**
     * Represents a failed outcome containing the error details.
     *
     * @property error The specific [AppError] describing what went wrong.
     * @property message An optional human-readable message, useful for logging or debugging.
     * @property throwable An optional underlying exception that caused the error.
     */
    data class Error<out D, out E : AppError>(
        val error: E,
        val message: String? = null,
        val throwable: Throwable? = null,
    ) : Result<D, E>
}
