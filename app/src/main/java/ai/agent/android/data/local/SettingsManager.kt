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
        val SYSTEM_PROMPT_PREFIX = stringPreferencesKey("system_prompt_prefix")
        val TOOL_USAGE_INSTRUCTION = stringPreferencesKey("tool_usage_instruction")
        val MCP_SERVER_URLS = stringSetPreferencesKey("mcp_server_urls")
        val DISABLED_APP_FUNCTIONS = stringSetPreferencesKey("disabled_app_functions")
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
}
