package ai.agent.android.domain.repositories

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
     * Retrieves the base URL for the local Ollama instance.
     * @return A Flow emitting the URL, or null if not set.
     */
    fun getOllamaBaseUrl(): Flow<String?>

    /**
     * Sets the base URL for the local Ollama instance.
     * @param url The URL to save (e.g., "http://192.168.1.100:11434"), or null to remove it.
     */
    suspend fun setOllamaBaseUrl(url: String?)
}
