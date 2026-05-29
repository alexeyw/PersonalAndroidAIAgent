package ai.agent.android.data.local

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.domain.models.McpAuth
import ai.agent.android.domain.models.McpServerConfig
import ai.agent.android.domain.models.McpTransport
import ai.agent.android.domain.models.TestProbeResult
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.models.UpdateMcpServerResult
import ai.agent.android.domain.repositories.SettingsRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Concrete implementation of [SettingsRepository] utilizing Androidx DataStore Preferences.
 *
 * @property dataStore The underlying DataStore instance for persistence.
 */
@Suppress("LargeClass") // 31-field DataStore facade by design; per-section split planned post-v0.1.
class SettingsManager @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private object PreferencesKeys {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val MAX_CONTEXT_LENGTH = intPreferencesKey("max_context_length")
        val TEMPERATURE = androidx.datastore.preferences.core.floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = androidx.datastore.preferences.core.floatPreferencesKey("top_p")
        val REQUIRES_USER_CONFIRMATION = booleanPreferencesKey("requires_user_confirmation")
        val SYSTEM_PROMPT_PREFIX = stringPreferencesKey("system_prompt_prefix")
        val TOOL_USAGE_INSTRUCTION = stringPreferencesKey("tool_usage_instruction")
        val MCP_SERVER_URLS = stringSetPreferencesKey("mcp_server_urls")
        val MCP_SERVERS_JSON = stringPreferencesKey("mcp_servers_json")
        val DISABLED_APP_FUNCTIONS = stringSetPreferencesKey("disabled_app_functions")
        val DISABLED_MCP_TOOLS = stringSetPreferencesKey("disabled_mcp_tools")
        val APP_FUNCTION_RISK_OVERRIDES = stringPreferencesKey("app_function_risk_overrides")
        val CURRENT_CHAT_SESSION_ID = stringPreferencesKey("current_chat_session_id")
        val MAX_MEMORY_CHUNKS_FOR_SEARCH = intPreferencesKey("max_memory_chunks_for_search")
        val MEMORY_SEARCH_TOP_K = intPreferencesKey("memory_search_top_k")
        val MEMORY_SEARCH_THRESHOLD =
            androidx.datastore.preferences.core.floatPreferencesKey("memory_search_threshold")
        val MEMORY_RECENCY_HALF_LIFE_DAYS = intPreferencesKey("memory_recency_half_life_days")
        val LOCAL_MODEL_BACKEND = stringPreferencesKey("local_model_backend")

        /**
         * Sentinel persisted right before a non-CPU LiteRT backend init is
         * attempted and cleared when the init returns successfully. If a
         * subsequent cold-start still sees this key set, the previous attempt
         * crashed the process during native init (e.g. GPU/NPU dispatch
         * library missing) — `LiteRTLlmEngine.initialize` then falls back
         * to CPU automatically.
         */
        val LAST_INIT_BACKEND_ATTEMPT = stringPreferencesKey("last_init_backend_attempt")
        val TOOL_CALL_TIMEOUT_MS = androidx.datastore.preferences.core.longPreferencesKey("tool_call_timeout_ms")
        val PIPELINE_MAX_STEPS = intPreferencesKey("pipeline_max_steps")
        val MEMORY_SUMMARY_DEFAULT_LIMIT = intPreferencesKey("memory_summary_default_limit")
        val DEFAULT_PIPELINE_ID = stringPreferencesKey("default_pipeline_id")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val CONSOLE_PREFERRED_TAB = stringPreferencesKey("console_preferred_tab")

        // Phase 22 / Task 9 — Settings redesign.
        val TOOL_APPROVAL_POLICY = stringPreferencesKey("tool_approval_policy")
        val BLOCK_DESTRUCTIVE_TOOLS = booleanPreferencesKey("block_destructive_tools")
        val BLOCK_NETWORK_FROM_LOCAL_MODEL = booleanPreferencesKey("block_network_from_local_model")
        val REPETITION_PENALTY = androidx.datastore.preferences.core.floatPreferencesKey("repetition_penalty")
        val AUTO_SUMMARIZE_THRESHOLD = androidx.datastore.preferences.core.floatPreferencesKey(
            "auto_summarize_threshold",
        )
        val LONG_RUNNING_TASKS_NOTIFICATIONS = booleanPreferencesKey("long_running_tasks_notifications")
        val LAST_TEST_PROBE_RESULT = stringPreferencesKey("last_test_probe_result")

        // Phase 25 / Task 1 — Embedding provider abstraction.
        val ACTIVE_EMBEDDING_PROVIDER_ID = stringPreferencesKey("active_embedding_provider_id")

        // Phase 25 / Task 2 — Memory write auto-extraction.
        val AUTO_EXTRACT_ENABLED = booleanPreferencesKey("auto_extract_enabled")

        // Phase 25 / Task 5 — Background memory compaction.
        val MEMORY_COMPACTION_ENABLED = booleanPreferencesKey("memory_compaction_enabled")
        val MEMORY_COMPACTION_AGE_DAYS = intPreferencesKey("memory_compaction_age_days")
        val MAX_MEMORY_CHUNKS = intPreferencesKey("max_memory_chunks")

        // Phase 25 / Task 6 — Memory observability.
        val VERBOSE_MEMORY_LOGGING_ENABLED = booleanPreferencesKey("verbose_memory_logging_enabled")
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

    override val hasCompletedOnboarding: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
        }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
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
            preferences[PreferencesKeys.MAX_CONTEXT_LENGTH] ?: SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT
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
            preferences[PreferencesKeys.TEMPERATURE] ?: SettingsDefaults.TEMPERATURE_DEFAULT
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
            preferences[PreferencesKeys.TOP_K] ?: SettingsDefaults.TOP_K_DEFAULT
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
            preferences[PreferencesKeys.TOP_P] ?: SettingsDefaults.TOP_P_DEFAULT
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

    override val mcpServers: Flow<List<McpServerConfig>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val json = preferences[PreferencesKeys.MCP_SERVERS_JSON]
            if (!json.isNullOrBlank()) {
                decodeMcpServers(json)
            } else {
                // Legacy fallback: read the old URL-only key. The first write through
                // [addMcpServer]/[updateMcpServer]/[removeMcpServer] persists the new
                // JSON form and the next read short-circuits above.
                (preferences[PreferencesKeys.MCP_SERVER_URLS] ?: emptySet())
                    .map { url -> McpServerConfig(url = url) }
            }
        }

    override suspend fun addMcpServer(config: McpServerConfig) {
        dataStore.edit { preferences ->
            val current = currentMcpServers(preferences)
            val without = current.filterNot { it.url == config.url }
            preferences[PreferencesKeys.MCP_SERVERS_JSON] = encodeMcpServers(without + config)
            preferences.remove(PreferencesKeys.MCP_SERVER_URLS)
        }
    }

    override suspend fun updateMcpServer(originalUrl: String, updated: McpServerConfig): UpdateMcpServerResult {
        // Read the current list once outside `edit { … }` so the collision
        // check can short-circuit *before* opening the write transaction.
        // DataStore serialises edits, so a concurrent write between this
        // read and the edit would only widen the window for a duplicate
        // to slip in by milliseconds — and the next call still detects
        // it. The user-visible bug is the silent collision; a transient
        // race is acceptable here.
        val snapshot = mcpServers.first()
        McpServerCollisionCheck
            .detectCollision(currentList = snapshot, originalUrl = originalUrl, newUrl = updated.url)
            ?.let { return it }
        dataStore.edit { preferences ->
            val current = currentMcpServers(preferences)
            val index = current.indexOfFirst { it.url == originalUrl }
            val next = if (index >= 0) {
                current.toMutableList().also { it[index] = updated }
            } else {
                current + updated
            }
            preferences[PreferencesKeys.MCP_SERVERS_JSON] = encodeMcpServers(next)
            preferences.remove(PreferencesKeys.MCP_SERVER_URLS)
        }
        return UpdateMcpServerResult.Success
    }

    override suspend fun removeMcpServer(url: String) {
        dataStore.edit { preferences ->
            val current = currentMcpServers(preferences)
            preferences[PreferencesKeys.MCP_SERVERS_JSON] = encodeMcpServers(current.filterNot { it.url == url })
            preferences.remove(PreferencesKeys.MCP_SERVER_URLS)
        }
    }

    /** Reads the current persisted list, falling back to the legacy URL-only key. */
    private fun currentMcpServers(preferences: Preferences): List<McpServerConfig> {
        val json = preferences[PreferencesKeys.MCP_SERVERS_JSON]
        if (!json.isNullOrBlank()) return decodeMcpServers(json)
        return (preferences[PreferencesKeys.MCP_SERVER_URLS] ?: emptySet())
            .map { url -> McpServerConfig(url = url) }
    }

    private fun encodeMcpServers(servers: List<McpServerConfig>): String {
        val array = JSONArray()
        servers.forEach { config ->
            val obj = JSONObject()
                .put("url", config.url)
                .put("transport", config.transport.wireId)
            if (!config.name.isNullOrBlank()) obj.put("name", config.name)
            encodeAuth(config.auth)?.let { obj.put("auth", it) }
            if (config.headers.isNotEmpty()) {
                val headers = JSONObject()
                config.headers.forEach { (k, v) -> headers.put(k, v) }
                obj.put("headers", headers)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun encodeAuth(auth: McpAuth): JSONObject? = when (auth) {
        is McpAuth.None -> null
        is McpAuth.Bearer -> JSONObject().put("type", "bearer").put("token", auth.token)
        is McpAuth.Basic -> JSONObject()
            .put("type", "basic")
            .put("username", auth.username)
            .put("password", auth.password)
        is McpAuth.ApiKey -> JSONObject()
            .put("type", "apiKey")
            .put("headerName", auth.headerName)
            .put("value", auth.value)
    }

    private fun decodeAuth(obj: JSONObject?): McpAuth {
        if (obj == null) return McpAuth.None
        return when (obj.optString("type")) {
            "bearer" -> McpAuth.Bearer(token = obj.optString("token"))
            "basic" -> McpAuth.Basic(
                username = obj.optString("username"),
                password = obj.optString("password"),
            )
            "apiKey" -> McpAuth.ApiKey(
                headerName = obj.optString("headerName"),
                value = obj.optString("value"),
            )
            else -> McpAuth.None
        }
    }

    private fun decodeMcpServers(json: String): List<McpServerConfig> = try {
        val array = JSONArray(json)
        buildList(capacity = array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() }
                val transport = McpTransport.fromWireId(obj.optString("transport").takeIf { it.isNotBlank() })
                val auth = decodeAuth(obj.optJSONObject("auth"))
                val headers = obj.optJSONObject("headers")?.let { headerObj ->
                    buildMap<String, String> {
                        val keys = headerObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, headerObj.optString(key))
                        }
                    }
                } ?: emptyMap()
                add(
                    McpServerConfig(
                        url = url,
                        name = name,
                        transport = transport,
                        auth = auth,
                        headers = headers,
                    ),
                )
            }
        }
    } catch (e: JSONException) {
        Timber.w(e, "Failed to decode MCP servers JSON; falling back to empty list")
        emptyList()
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

    override val disabledMcpTools: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DISABLED_MCP_TOOLS] ?: emptySet()
        }

    override suspend fun setDisabledMcpTools(toolIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISABLED_MCP_TOOLS] = toolIds
        }
    }

    override val appFunctionRiskOverrides: Flow<Map<String, ToolRisk>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            decodeRiskOverrides(preferences[PreferencesKeys.APP_FUNCTION_RISK_OVERRIDES])
        }

    override suspend fun setAppFunctionRiskOverride(toolName: String, risk: ToolRisk) {
        dataStore.edit { preferences ->
            val current = decodeRiskOverrides(preferences[PreferencesKeys.APP_FUNCTION_RISK_OVERRIDES])
            val merged = current + (toolName to risk)
            preferences[PreferencesKeys.APP_FUNCTION_RISK_OVERRIDES] = encodeRiskOverrides(merged)
        }
    }

    private fun decodeRiskOverrides(raw: String?): Map<String, ToolRisk> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key)
                    val risk = runCatching { ToolRisk.valueOf(value) }.getOrNull()
                    if (risk != null) {
                        put(key, risk)
                    } else {
                        Timber.w("Dropping AppFunction risk override for $key — unknown risk value '$value'")
                    }
                }
            }
        } catch (e: org.json.JSONException) {
            Timber.w(e, "Failed to parse app_function_risk_overrides — falling back to empty map")
            emptyMap()
        }
    }

    private fun encodeRiskOverrides(overrides: Map<String, ToolRisk>): String {
        val json = JSONObject()
        for ((name, risk) in overrides) {
            json.put(name, risk.name)
        }
        return json.toString()
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

    override val consolePreferredConsoleTabName: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CONSOLE_PREFERRED_TAB] ?: CONSOLE_PREFERRED_TAB_DEFAULT
        }

    override suspend fun setConsolePreferredConsoleTabName(name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONSOLE_PREFERRED_TAB] = name
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
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS_FOR_SEARCH]
                ?: SettingsDefaults.MEMORY_CHUNK_SEARCH_LIMIT_DEFAULT
        }

    override suspend fun setMaxMemoryChunksForSearch(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS_FOR_SEARCH] = limit
        }
    }

    override val memorySearchTopK: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_SEARCH_TOP_K]
                ?: SettingsDefaults.MEMORY_SEARCH_TOP_K_DEFAULT
        }

    override suspend fun setMemorySearchTopK(topK: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_SEARCH_TOP_K] = topK
        }
    }

    override val memorySearchThreshold: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_SEARCH_THRESHOLD]
                ?: SettingsDefaults.MEMORY_SEARCH_THRESHOLD_DEFAULT
        }

    override suspend fun setMemorySearchThreshold(threshold: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_SEARCH_THRESHOLD] = threshold
        }
    }

    override val memoryRecencyHalfLifeDays: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_RECENCY_HALF_LIFE_DAYS]
                ?: SettingsDefaults.MEMORY_RECENCY_HALF_LIFE_DAYS_DEFAULT
        }

    override suspend fun setMemoryRecencyHalfLifeDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_RECENCY_HALF_LIFE_DAYS] = days
        }
    }

    override val defaultPipelineId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_PIPELINE_ID]
        }

    override suspend fun setDefaultPipelineId(pipelineId: String?) {
        dataStore.edit { preferences ->
            if (pipelineId == null) {
                preferences.remove(PreferencesKeys.DEFAULT_PIPELINE_ID)
            } else {
                preferences[PreferencesKeys.DEFAULT_PIPELINE_ID] = pipelineId
            }
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
            preferences[PreferencesKeys.LOCAL_MODEL_BACKEND] ?: LocalBackend.CPU.key
        }

    override suspend fun setLocalModelBackend(backend: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_MODEL_BACKEND] = backend
        }
    }

    override val activeEmbeddingProviderId: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_EMBEDDING_PROVIDER_ID]
                ?: SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT
        }

    override suspend fun setActiveEmbeddingProviderId(id: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_EMBEDDING_PROVIDER_ID] = id
        }
    }

    override val autoExtractEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_EXTRACT_ENABLED] ?: SettingsDefaults.AUTO_EXTRACT_ENABLED_DEFAULT
        }

    override val memoryCompactionEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_COMPACTION_ENABLED]
                ?: SettingsDefaults.MEMORY_COMPACTION_ENABLED_DEFAULT
        }

    override suspend fun setMemoryCompactionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_COMPACTION_ENABLED] = enabled
        }
    }

    override val verboseMemoryLoggingEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.VERBOSE_MEMORY_LOGGING_ENABLED]
                ?: SettingsDefaults.VERBOSE_MEMORY_LOGGING_ENABLED_DEFAULT
        }

    override suspend fun setVerboseMemoryLoggingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VERBOSE_MEMORY_LOGGING_ENABLED] = enabled
        }
    }

    override val memoryCompactionAgeDays: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_COMPACTION_AGE_DAYS]
                ?: SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_DEFAULT
        }

    override suspend fun setMemoryCompactionAgeDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_COMPACTION_AGE_DAYS] = days
        }
    }

    override val maxMemoryChunks: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS]
                ?: SettingsDefaults.MAX_MEMORY_CHUNKS_DEFAULT
        }

    override suspend fun setMaxMemoryChunks(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_MEMORY_CHUNKS] = limit
        }
    }

    override suspend fun setAutoExtractEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_EXTRACT_ENABLED] = enabled
        }
    }

    override val lastInitBackendAttempt: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.LAST_INIT_BACKEND_ATTEMPT] }

    override suspend fun setLastInitBackendAttempt(backendKey: String?) {
        dataStore.edit { preferences ->
            if (backendKey == null) {
                preferences.remove(PreferencesKeys.LAST_INIT_BACKEND_ATTEMPT)
            } else {
                preferences[PreferencesKeys.LAST_INIT_BACKEND_ATTEMPT] = backendKey
            }
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
            preferences[PreferencesKeys.TOOL_CALL_TIMEOUT_MS] ?: SettingsDefaults.TOOL_CALL_TIMEOUT_MS_DEFAULT
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
            preferences[PreferencesKeys.PIPELINE_MAX_STEPS] ?: SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT
        }

    override suspend fun setPipelineMaxSteps(steps: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIPELINE_MAX_STEPS] = steps.coerceIn(
                SettingsDefaults.PIPELINE_MAX_STEPS_MIN,
                SettingsDefaults.PIPELINE_MAX_STEPS_MAX,
            )
        }
    }

    override val crashReportingEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.CRASH_REPORTING_ENABLED] ?: false
        }

    override suspend fun setCrashReportingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CRASH_REPORTING_ENABLED] = enabled
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

    override val toolApprovalPolicy: Flow<ToolApprovalPolicy> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val storedKey = preferences[PreferencesKeys.TOOL_APPROVAL_POLICY]
            if (storedKey != null) {
                ToolApprovalPolicy.fromKey(storedKey)
            } else {
                // One-shot migration from the legacy boolean key.
                // true  → SensitiveOrDestructive (default-with-care).
                // false → NeverPrompt (only way the legacy UI let users skip destructive prompts).
                when (preferences[PreferencesKeys.REQUIRES_USER_CONFIRMATION]) {
                    false -> ToolApprovalPolicy.NeverPrompt
                    true -> ToolApprovalPolicy.SensitiveOrDestructive
                    null -> ToolApprovalPolicy.DEFAULT
                }
            }
        }

    override suspend fun setToolApprovalPolicy(policy: ToolApprovalPolicy) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOOL_APPROVAL_POLICY] = policy.key
            // Keep the legacy flag in sync so any consumer still reading the
            // old boolean (until they migrate) sees a coherent value:
            // anything other than NeverPrompt counts as "ask sometimes".
            preferences[PreferencesKeys.REQUIRES_USER_CONFIRMATION] = policy != ToolApprovalPolicy.NeverPrompt
        }
    }

    override val blockDestructiveTools: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.BLOCK_DESTRUCTIVE_TOOLS] ?: false
        }

    override suspend fun setBlockDestructiveTools(blocked: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_DESTRUCTIVE_TOOLS] = blocked
        }
    }

    override val blockNetworkFromLocalModel: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.BLOCK_NETWORK_FROM_LOCAL_MODEL] ?: false
        }

    override suspend fun setBlockNetworkFromLocalModel(blocked: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_NETWORK_FROM_LOCAL_MODEL] = blocked
        }
    }

    override val repetitionPenalty: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.REPETITION_PENALTY] ?: SettingsDefaults.REPETITION_PENALTY_DEFAULT
        }

    override suspend fun setRepetitionPenalty(value: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPETITION_PENALTY] = value.coerceIn(
                SettingsDefaults.REPETITION_PENALTY_MIN,
                SettingsDefaults.REPETITION_PENALTY_MAX,
            )
        }
    }

    override val autoSummarizeThreshold: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZE_THRESHOLD]
                ?: SettingsDefaults.AUTO_SUMMARIZE_THRESHOLD_DEFAULT
        }

    override suspend fun setAutoSummarizeThreshold(threshold: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SUMMARIZE_THRESHOLD] = threshold.coerceIn(0f, 1f)
        }
    }

    override val longRunningTaskNotificationsEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LONG_RUNNING_TASKS_NOTIFICATIONS] ?: true
        }

    override suspend fun setLongRunningTaskNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LONG_RUNNING_TASKS_NOTIFICATIONS] = enabled
        }
    }

    override val lastTestProbeResult: Flow<TestProbeResult?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            decodeTestProbeResult(preferences[PreferencesKeys.LAST_TEST_PROBE_RESULT])
        }

    override suspend fun setLastTestProbeResult(result: TestProbeResult?) {
        dataStore.edit { preferences ->
            if (result == null) {
                preferences.remove(PreferencesKeys.LAST_TEST_PROBE_RESULT)
            } else {
                preferences[PreferencesKeys.LAST_TEST_PROBE_RESULT] = encodeTestProbeResult(result)
            }
        }
    }

    override suspend fun resetSamplingDefaults() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] = SettingsDefaults.TEMPERATURE_DEFAULT
            preferences[PreferencesKeys.TOP_K] = SettingsDefaults.TOP_K_DEFAULT
            preferences[PreferencesKeys.TOP_P] = SettingsDefaults.TOP_P_DEFAULT
            preferences[PreferencesKeys.REPETITION_PENALTY] = SettingsDefaults.REPETITION_PENALTY_DEFAULT
            preferences[PreferencesKeys.MAX_CONTEXT_LENGTH] = SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT
            preferences[PreferencesKeys.PIPELINE_MAX_STEPS] = SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT
        }
    }

    private fun encodeTestProbeResult(result: TestProbeResult): String = JSONObject().apply {
        put("tokens", result.tokensGenerated)
        put("durationMs", result.durationMs)
        put("timestampMs", result.timestampMs)
        put("success", result.success)
        if (result.errorMessage != null) put("error", result.errorMessage)
    }.toString()

    private fun decodeTestProbeResult(raw: String?): TestProbeResult? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            TestProbeResult(
                tokensGenerated = json.optInt("tokens", 0),
                durationMs = json.optLong("durationMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L),
                success = json.optBoolean("success", false),
                errorMessage = json.optString("error", "").takeIf { it.isNotBlank() },
            )
        } catch (e: JSONException) {
            Timber.w(e, "Failed to parse last_test_probe_result — clearing")
            null
        }
    }

    private companion object {
        const val DEFAULT_MEMORY_SUMMARY_LIMIT = 5

        /**
         * Default value for [PreferencesKeys.CONSOLE_PREFERRED_TAB] on a
         * fresh install. Mirrors the enum name of
         * `app.knotwork.design.components.console.ConsoleTab.Logs` — kept as
         * a raw string so this data-layer constant stays free of the
         * `:catalog` dependency.
         */
        const val CONSOLE_PREFERRED_TAB_DEFAULT = "Logs"
    }
}
