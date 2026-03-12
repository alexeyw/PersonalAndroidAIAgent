package ai.agent.android.domain.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing application-wide settings and user preferences.
 * 
 * Provides abstraction over the underlying persistence mechanism (e.g., DataStore, SharedPreferences).
 */
interface SettingsRepository {
    
    /**
     * A [Flow] representing the current state of the first launch flag.
     * Emits `true` if it's the user's first time launching the app, `false` otherwise.
     */
    val isFirstLaunch: Flow<Boolean>

    /**
     * Updates the first launch flag.
     * 
     * @param isFirstLaunch The new value to set.
     */
    suspend fun setFirstLaunch(isFirstLaunch: Boolean)

    /**
     * A [Flow] representing the saved HuggingFace authorization token.
     */
    val huggingFaceAuthToken: Flow<String?>

    /**
     * Updates the HuggingFace authorization token.
     *
     * @param token The new token to save, or null to clear it.
     */
    suspend fun setHuggingFaceAuthToken(token: String?)

    /**
     * A [Flow] representing the maximum allowed context length (e.g., in characters or tokens).
     */
    val maxContextLength: Flow<Int>

    /**
     * Updates the maximum allowed context length.
     *
     * @param length The new maximum length to set.
     */
    suspend fun setMaxContextLength(length: Int)

    /**
     * A [Flow] representing the system prompt prefix.
     */
    val systemPromptPrefix: Flow<String>

    /**
     * Updates the system prompt prefix.
     *
     * @param prompt The new prompt to set.
     */
    suspend fun setSystemPromptPrefix(prompt: String)

    /**
     * A [Flow] representing the tool usage instruction prompt.
     */
    val toolUsageInstruction: Flow<String>

    /**
     * Updates the tool usage instruction prompt.
     *
     * @param instruction The new instruction to set.
     */
    suspend fun setToolUsageInstruction(instruction: String)
}
