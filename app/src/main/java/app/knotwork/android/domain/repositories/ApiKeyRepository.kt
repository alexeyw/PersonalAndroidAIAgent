package app.knotwork.android.domain.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing API keys for external LLM providers and
 * configuration settings for local network providers (like Ollama).
 */
interface ApiKeyRepository {

    /**
     * Retrieves the OpenAI API key.
     * @return A Flow emitting the key, or null if not set.
     */
    fun getOpenAIKey(): Flow<String?>

    /**
     * Sets the OpenAI API key.
     * @param key The key to save, or null to remove it.
     */
    suspend fun setOpenAIKey(key: String?)

    /**
     * Retrieves the OpenAI model name.
     * @return A Flow emitting the model name, or null if not set.
     */
    fun getOpenAIModel(): Flow<String?>

    /**
     * Sets the OpenAI model name.
     * @param model The model to save, or null to remove it.
     */
    suspend fun setOpenAIModel(model: String?)

    /**
     * Retrieves the Anthropic API key.
     * @return A Flow emitting the key, or null if not set.
     */
    fun getAnthropicKey(): Flow<String?>

    /**
     * Sets the Anthropic API key.
     * @param key The key to save, or null to remove it.
     */
    suspend fun setAnthropicKey(key: String?)

    /**
     * Retrieves the Anthropic model name.
     * @return A Flow emitting the model name, or null if not set.
     */
    fun getAnthropicModel(): Flow<String?>

    /**
     * Sets the Anthropic model name.
     * @param model The model to save, or null to remove it.
     */
    suspend fun setAnthropicModel(model: String?)

    /**
     * Retrieves the Google (Gemini) API key.
     * @return A Flow emitting the key, or null if not set.
     */
    fun getGoogleKey(): Flow<String?>

    /**
     * Sets the Google (Gemini) API key.
     * @param key The key to save, or null to remove it.
     */
    suspend fun setGoogleKey(key: String?)

    /**
     * Retrieves the Google model name.
     * @return A Flow emitting the model name, or null if not set.
     */
    fun getGoogleModel(): Flow<String?>

    /**
     * Sets the Google model name.
     * @param model The model to save, or null to remove it.
     */
    suspend fun setGoogleModel(model: String?)

    /**
     * Retrieves the DeepSeek API key.
     * @return A Flow emitting the key, or null if not set.
     */
    fun getDeepSeekKey(): Flow<String?>

    /**
     * Sets the DeepSeek API key.
     * @param key The key to save, or null to remove it.
     */
    suspend fun setDeepSeekKey(key: String?)

    /**
     * Retrieves the DeepSeek model name.
     * @return A Flow emitting the model name, or null if not set.
     */
    fun getDeepSeekModel(): Flow<String?>

    /**
     * Sets the DeepSeek model name.
     * @param model The model to save, or null to remove it.
     */
    suspend fun setDeepSeekModel(model: String?)

    /**
     * Retrieves the base URL for the local Ollama instance.
     * @return A Flow emitting the URL, or null if not set.
     */
    fun getOllamaBaseUrl(): Flow<String?>

    /**
     * Sets the base URL for the local Ollama instance.
     * @param url The URL to save (e.g., "http://192.168.1.100:11434"), or null to remove it.
     */
    suspend fun setOllamaBaseUrl(url: String?)

    /**
     * Retrieves the Ollama model name.
     * @return A Flow emitting the model name, or null if not set.
     */
    fun getOllamaModelName(): Flow<String?>

    /**
     * Sets the Ollama model name.
     * @param model The model name to save, or null to remove it.
     */
    suspend fun setOllamaModelName(model: String?)

    /**
     * Retrieves the Ollama context window size.
     * @return A Flow emitting the context window size, defaults to 4096.
     */
    fun getOllamaContextWindowSize(): Flow<Int>

    /**
     * Sets the Ollama context window size.
     * @param size The context window size to save.
     */
    suspend fun setOllamaContextWindowSize(size: Int)
}
