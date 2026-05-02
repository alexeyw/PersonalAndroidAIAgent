package ai.agent.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

import ai.agent.android.domain.constants.DefaultPrompts
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Concrete implementation of [SettingsRepository] utilizing Androidx DataStore Preferences.
 * 
 * @property dataStore The underlying DataStore instance for persistence.
 */
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val MAX_CONTEXT_LENGTH = intPreferencesKey("max_context_length")
        val TEMPERATURE = androidx.datastore.preferences.core.floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = androidx.datastore.preferences.core.floatPreferencesKey("top_p")
        val REQUIRES_USER_CONFIRMATION = booleanPreferencesKey("requires_user_confirmation")
        val SYSTEM_PROMPT_PREFIX = stringPreferencesKey("system_prompt_prefix")
        val TOOL_USAGE_INSTRUCTION = stringPreferencesKey("tool_usage_instruction")
        val MCP_SERVER_URLS = stringSetPreferencesKey("mcp_server_urls")
        val DISABLED_APP_FUNCTIONS = stringSetPreferencesKey("disabled_app_functions")
        val CURRENT_CHAT_SESSION_ID = stringPreferencesKey("current_chat_session_id")
        val MAX_MEMORY_CHUNKS_FOR_SEARCH = intPreferencesKey("max_memory_chunks_for_search")
        val LOCAL_MODEL_BACKEND = stringPreferencesKey("local_model_backend")
        val TOOL_CALL_TIMEOUT_MS = androidx.datastore.preferences.core.longPreferencesKey("tool_call_timeout_ms")
        val PIPELINE_MAX_STEPS = intPreferencesKey("pipeline_max_steps")
        val MEMORY_SUMMARY_DEFAULT_LIMIT = intPreferencesKey("memory_summary_default_limit")
    }

    override val isFirstLaunch: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] ?: true
        }

    override suspend fun setFirstLaunch(isFirstLaunch: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] = isFirstLaunch
        }
    }

    override val huggingFaceAuthToken: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HUGGING_FACE_TOKEN]
        }

    override suspend fun setHuggingFaceAuthToken(token: String?) {
        dataStore.edit { preferences ->
            if (token == null) {
                preferences.remove(PreferencesKeys.HUGGING_FACE_TOKEN)
            } else {
                preferences[PreferencesKeys.HUGGING_FACE_TOKEN] = token
            }
        }
    }

    override val maxContextLength: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MAX_CONTEXT_LENGTH] ?: 4000
        }

    override suspend fun setMaxContextLength(length: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_CONTEXT_LENGTH] = length
        }
    }

    override val temperature: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] ?: 0.7f
        }

    override suspend fun setTemperature(temperature: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] = temperature
        }
    }

    override val topK: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TOP_K] ?: 40
        }

    override suspend fun setTopK(topK: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOP_K] = topK
        }
    }

    override val topP: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TOP_P] ?: 0.9f
        }

    override suspend fun setTopP(topP: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOP_P] = topP
        }
    }

    override val requiresUserConfirmation: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.REQUIRES_USER_CONFIRMATION] ?: true
        }

    override suspend fun setRequiresUserConfirmation(required: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REQUIRES_USER_CONFIRMATION] = required
        }
    }

    override val systemPromptPrefix: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT_PREFIX] ?: DefaultPrompts.SYSTEM_PROMPT_PREFIX
        }

    override suspend fun setSystemPromptPrefix(prompt: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT_PREFIX] = prompt
        }
    }

    override val toolUsageInstruction: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TOOL_USAGE_INSTRUCTION] ?: DefaultPrompts.TOOL_USAGE_INSTRUCTION
        }

    override suspend fun setToolUsageInstruction(instruction: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOOL_USAGE_INSTRUCTION] = instruction
        }
    }

    override val mcpServerUrls: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MCP_SERVER_URLS] ?: emptySet()
        }

    override suspend fun addMcpServerUrl(url: String) {
        dataStore.edit { preferences ->
            val currentUrls = preferences[PreferencesKeys.MCP_SERVER_URLS] ?: emptySet()
            preferences[PreferencesKeys.MCP_SERVER_URLS] = currentUrls + url
        }
    }

    override suspend fun removeMcpServerUrl(url: String) {
        dataStore.edit { preferences ->
            val currentUrls = preferences[PreferencesKeys.MCP_SERVER_URLS] ?: emptySet()
            preferences[PreferencesKeys.MCP_SERVER_URLS] = currentUrls - url
        }
    }

    override val disabledAppFunctions: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DISABLED_APP_FUNCTIONS] ?: emptySet()
        }

    override suspend fun setDisabledAppFunctions(functions: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISABLED_APP_FUNCTIONS] = functions
        }
    }

    override val currentChatSessionId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_SESSION_ID]
        }

    override suspend fun setCurrentChatSessionId(sessionId: String?) {
        dataStore.edit { preferences ->
            if (sessionId == null) {
                preferences.remove(PreferencesKeys.CURRENT_CHAT_SESSION_ID)
            } else {
                preferences[PreferencesKeys.CURRENT_CHAT_SESSION_ID] = sessionId
            }
        }
    }

    override val maxMemoryChunksForSearch: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS_FOR_SEARCH] ?: 1000
        }

    override suspend fun setMaxMemoryChunksForSearch(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS_FOR_SEARCH] = limit
        }
    }

    override val localModelBackend: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LOCAL_MODEL_BACKEND] ?: "CPU"
        }

    override suspend fun setLocalModelBackend(backend: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_MODEL_BACKEND] = backend
        }
    }

    override val toolCallTimeoutMs: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TOOL_CALL_TIMEOUT_MS] ?: 60_000L
        }

    override suspend fun setToolCallTimeoutMs(timeoutMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOOL_CALL_TIMEOUT_MS] = timeoutMs
        }
    }

    override val pipelineMaxSteps: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.PIPELINE_MAX_STEPS] ?: 15
        }

    override suspend fun setPipelineMaxSteps(steps: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIPELINE_MAX_STEPS] = steps.coerceIn(5, 100)
        }
    }

    override val memorySummaryDefaultLimit: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_SUMMARY_DEFAULT_LIMIT] ?: DEFAULT_MEMORY_SUMMARY_LIMIT
        }

    override suspend fun setMemorySummaryDefaultLimit(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_SUMMARY_DEFAULT_LIMIT] = limit
        }
    }

    private companion object {
        const val DEFAULT_MEMORY_SUMMARY_LIMIT = 5
    }
}
