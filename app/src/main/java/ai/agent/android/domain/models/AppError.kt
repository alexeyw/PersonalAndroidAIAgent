package ai.agent.android.domain.models

/**
 * A sealed interface representing the generic types of errors that can occur within the application.
 * 
 * This helps in abstracting away implementation-specific exceptions (like Retrofit exceptions
 * or Room exceptions) into domain-level errors that the presentation layer can easily understand
 * and handle.
 */
sealed interface AppError {
    
    /**
     * Represents errors related to network connectivity or remote API calls.
     */
    interface Network : AppError
    
    /**
     * Represents errors related to local data storage (e.g., Room database, DataStore).
     */
    interface Database : AppError
    
    /**
     * Represents errors related to the Android system or device capabilities
     * (e.g., missing permissions, out of memory, missing hardware features).
     */
    interface System : AppError
    
    /**
     * Represents any other error that doesn't fit into the predefined categories.
     * Often wraps an unexpected exception.
     */
    interface Unknown : AppError
}
