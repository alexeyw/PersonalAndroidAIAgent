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
     * A [Flow] representing the sampling temperature for generation.
     */
    val temperature: Flow<Float>

    /**
     * Updates the sampling temperature.
     */
    suspend fun setTemperature(temperature: Float)

    /**
     * A [Flow] representing the top-k sampling parameter for generation.
     */
    val topK: Flow<Int>

    /**
     * Updates the top-k sampling parameter.
     */
    suspend fun setTopK(topK: Int)

    /**
     * A [Flow] representing the top-p sampling parameter for generation.
     */
    val topP: Flow<Float>

    /**
     * Updates the top-p sampling parameter.
     */
    suspend fun setTopP(topP: Float)

    /**
     * A [Flow] indicating if user confirmation is required for critical actions (Human-in-the-loop).
     */
    val requiresUserConfirmation: Flow<Boolean>

    /**
     * Updates the requirement for user confirmation.
     */
    suspend fun setRequiresUserConfirmation(required: Boolean)

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

    /**
     * A [Flow] representing the set of connected MCP server URLs.
     */
    val mcpServerUrls: Flow<Set<String>>

    /**
     * Adds an MCP server URL.
     */
    suspend fun addMcpServerUrl(url: String)

    /**
     * Removes an MCP server URL.
     */
    suspend fun removeMcpServerUrl(url: String)

    /**
     * A [Flow] representing the set of disabled local app function names.
     */
    val disabledAppFunctions: Flow<Set<String>>

    /**
     * Updates the set of disabled local app functions.
     */
    suspend fun setDisabledAppFunctions(functions: Set<String>)

    /**
     * A [Flow] representing the current active chat session ID.
     */
    val currentChatSessionId: Flow<String?>

    /**
     * Updates the current active chat session ID.
     */
    suspend fun setCurrentChatSessionId(sessionId: String?)

    /**
     * A [Flow] representing the maximum number of memory chunks to load for similarity search.
     */
    val maxMemoryChunksForSearch: Flow<Int>

    /**
     * Updates the maximum number of memory chunks for similarity search.
     *
     * @param limit The new limit.
     */
    suspend fun setMaxMemoryChunksForSearch(limit: Int)

    /**
     * A [Flow] emitting the wire key of the selected local-model backend
     * ([ai.agent.android.domain.models.LocalBackend.key]). Stored as a raw string for
     * backward compatibility with DataStore values written before the typed enum existed.
     */
    val localModelBackend: Flow<String>

    /**
     * Updates the selected backend for the local model.
     *
     * @param backend The new backend wire key; use
     *        [ai.agent.android.domain.models.LocalBackend.key] to obtain it.
     */
    suspend fun setLocalModelBackend(backend: String)

    /**
     * A [Flow] representing the timeout in milliseconds for tool approval requests.
     * After this duration without a user response, the approval is considered timed out.
     */
    val toolCallTimeoutMs: Flow<Long>

    /**
     * Updates the tool call approval timeout.
     *
     * @param timeoutMs The new timeout in milliseconds.
     */
    suspend fun setToolCallTimeoutMs(timeoutMs: Long)

    /**
     * A [Flow] representing the maximum number of pipeline execution steps.
     * Prevents infinite loops in pipeline graphs. Valid range: 5–100.
     */
    val pipelineMaxSteps: Flow<Int>

    /**
     * Updates the maximum number of pipeline execution steps.
     *
     * @param steps The new limit. Will be coerced to the range 5–100.
     */
    suspend fun setPipelineMaxSteps(steps: Int)

    /**
     * A [Flow] representing the id of the pipeline the user has marked as
     * default. `null` means no explicit choice — callers should fall back
     * to the first pipeline returned by `PipelineRepository.getAllPipelines()`
     * (the same convention used before this setting was introduced).
     *
     * Set on first launch by `InitializeAppUseCase` to the seeded
     * `Default System Pipeline` so the default is unambiguous from the
     * start. Cleared automatically when the marked pipeline is deleted.
     */
    val defaultPipelineId: Flow<String?>

    /**
     * Updates the user-marked default pipeline id. Pass `null` to clear
     * the marker (the resolution then falls back to the first pipeline
     * in the library).
     *
     * @param pipelineId Pipeline id to mark as default, or `null` to clear.
     */
    suspend fun setDefaultPipelineId(pipelineId: String?)

    /**
     * A [Flow] representing the default number of recent memory chunks rendered
     * by the `$MEMORY_SUMMARY` prompt variable. Defaults to 5.
     */
    val memorySummaryDefaultLimit: Flow<Int>

    /**
     * Updates the default number of recent memory chunks shown by `$MEMORY_SUMMARY`.
     *
     * @param limit The new chunk count. Values `<= 0` are valid and disable the
     * variable (it resolves to an empty string).
     */
    suspend fun setMemorySummaryDefaultLimit(limit: Int)
}
