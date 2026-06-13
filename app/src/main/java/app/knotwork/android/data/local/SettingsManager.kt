package app.knotwork.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.knotwork.android.data.local.crypto.AeadCipher
import app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore
import app.knotwork.android.data.local.crypto.SecureValueUnreadableException
import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.models.LocalBackend
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTransport
import app.knotwork.android.domain.models.TestProbeResult
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.models.UpdateMcpServerResult
import app.knotwork.android.domain.repositories.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Concrete implementation of [SettingsRepository] utilizing Androidx DataStore Preferences.
 *
 * Secret payloads are **not** kept in DataStore: the HuggingFace access token lives in a
 * [KeystoreBackedPrefsStore] (AES-GCM under a dedicated Android Keystore key), with the same
 * re-enterable-secret recovery policy as [ApiKeyManager] — an undecryptable value is dropped
 * and reported as unset. A token persisted by earlier releases in plain DataStore is migrated
 * into the encrypted store on the first read and removed from DataStore.
 *
 * @property dataStore The underlying DataStore instance for persistence.
 * @property context The application context backing the encrypted secrets store.
 * @param cipher The AEAD boundary used to protect stored secrets.
 */
@Suppress("LargeClass") // 31-field DataStore facade by design; per-section split planned post-v0.1.
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
    cipher: AeadCipher,
) : SettingsRepository {

    private val secretsStore = KeystoreBackedPrefsStore(
        context = context,
        prefsName = SECRETS_PREFS_NAME,
        keyAlias = SECRETS_KEY_ALIAS,
        cipher = cipher,
    )

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
        val MEMORY_LAST_COMPACTED_AT =
            androidx.datastore.preferences.core.longPreferencesKey("memory_last_compacted_at")
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
        val WORKSPACE_MAX_FILE_SIZE_BYTES =
            androidx.datastore.preferences.core.longPreferencesKey("workspace_max_file_size_bytes")
        val WORKSPACE_MAX_TOTAL_BYTES =
            androidx.datastore.preferences.core.longPreferencesKey("workspace_max_total_bytes")
        val WORKSPACE_READ_TOKEN_BUDGET = intPreferencesKey("workspace_read_token_budget")
        val PIPELINE_MAX_STEPS = intPreferencesKey("pipeline_max_steps")
        val RESUME_MAX_AGE_HOURS = intPreferencesKey("resume_max_age_hours")
        val BACKGROUND_APPROVAL_WINDOW_HOURS = intPreferencesKey("background_approval_window_hours")
        val TRACE_RETENTION_RUNS_PER_SESSION = intPreferencesKey("trace_retention_runs_per_session")
        val TRACE_RETENTION_MAX_AGE_DAYS = intPreferencesKey("trace_retention_max_age_days")
        val MEMORY_SUMMARY_DEFAULT_LIMIT = intPreferencesKey("memory_summary_default_limit")
        val DEFAULT_PIPELINE_ID = stringPreferencesKey("default_pipeline_id")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val CONSOLE_PREFERRED_TAB = stringPreferencesKey("console_preferred_tab")

        // Settings redesign.
        val TOOL_APPROVAL_POLICY = stringPreferencesKey("tool_approval_policy")
        val BLOCK_DESTRUCTIVE_TOOLS = booleanPreferencesKey("block_destructive_tools")
        val BLOCK_NETWORK_FROM_LOCAL_MODEL = booleanPreferencesKey("block_network_from_local_model")
        val REPETITION_PENALTY = androidx.datastore.preferences.core.floatPreferencesKey("repetition_penalty")
        val AUTO_SUMMARIZE_THRESHOLD = androidx.datastore.preferences.core.floatPreferencesKey(
            "auto_summarize_threshold",
        )
        val LONG_RUNNING_TASKS_NOTIFICATIONS = booleanPreferencesKey("long_running_tasks_notifications")
        val SCHEDULED_TASK_NOTIFICATIONS = booleanPreferencesKey("scheduled_task_notifications")
        val LAST_TEST_PROBE_RESULT = stringPreferencesKey("last_test_probe_result")

        // Embedding provider abstraction.
        val ACTIVE_EMBEDDING_PROVIDER_ID = stringPreferencesKey("active_embedding_provider_id")
        val LAST_REEMBED_PROVIDER_ID = stringPreferencesKey("last_reembed_provider_id")

        // Memory write auto-extraction.
        val AUTO_EXTRACT_ENABLED = booleanPreferencesKey("auto_extract_enabled")

        // Background memory compaction.
        val MEMORY_COMPACTION_ENABLED = booleanPreferencesKey("memory_compaction_enabled")
        val MEMORY_COMPACTION_AGE_DAYS = intPreferencesKey("memory_compaction_age_days")
        val MAX_MEMORY_CHUNKS = intPreferencesKey("max_memory_chunks")

        // Memory observability.
        val VERBOSE_MEMORY_LOGGING_ENABLED = booleanPreferencesKey("verbose_memory_logging_enabled")
    }

    /**
     * Entry names inside [secretsStore]. Distinct from [PreferencesKeys]: these are slots in
     * the Keystore-backed encrypted store, not DataStore preference keys.
     */
    private object SecretKeys {
        const val HUGGING_FACE_TOKEN = "hugging_face_token"
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

    /**
     * In-memory mirror of the encrypted HuggingFace-token entry. Initialized lazily from the
     * encrypted store with the re-enterable-secret policy applied; updated by
     * [setHuggingFaceAuthToken] and by the one-time legacy migration.
     */
    private val huggingFaceTokenFlow by lazy { MutableStateFlow(readHuggingFaceTokenOrNull()) }

    /** Serializes the legacy-DataStore migration so concurrent collectors run it once. */
    private val huggingFaceMigrationMutex = Mutex()
    private var huggingFaceMigrationDone = false

    override val huggingFaceAuthToken: Flow<String?> = flow {
        migrateLegacyHuggingFaceToken()
        emitAll(huggingFaceTokenFlow)
    }

    override suspend fun setHuggingFaceAuthToken(token: String?) {
        if (token == null) {
            secretsStore.remove(SecretKeys.HUGGING_FACE_TOKEN)
        } else {
            secretsStore.putString(SecretKeys.HUGGING_FACE_TOKEN, token)
        }
        huggingFaceTokenFlow.value = token
        // Any explicit write supersedes whatever a pre-migration release left in DataStore;
        // dropping the legacy key here also makes the one-time migration a no-op.
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.HUGGING_FACE_TOKEN)
        }
    }

    /**
     * Reads the encrypted token entry, applying the re-enterable-secret recovery policy:
     * an entry that cannot be decrypted is dropped and reported as absent (the user pastes
     * the token again), never propagated as an error.
     */
    private fun readHuggingFaceTokenOrNull(): String? = try {
        secretsStore.getString(SecretKeys.HUGGING_FACE_TOKEN)
    } catch (e: SecureValueUnreadableException) {
        Timber.e(e, "Stored HuggingFace token is unreadable; treating it as unset.")
        secretsStore.remove(SecretKeys.HUGGING_FACE_TOKEN)
        null
    }

    /**
     * One-time move of a token persisted by earlier releases in plain DataStore into the
     * encrypted store. The encrypted copy is committed synchronously **before** the legacy
     * entry is removed, so a crash in between leaves both copies rather than neither; if the
     * encrypted store already holds a token, the legacy leftover is just deleted. An
     * [IOException] while reading DataStore defers the migration to the next collection
     * instead of failing the flow.
     */
    private suspend fun migrateLegacyHuggingFaceToken() {
        if (huggingFaceMigrationDone) return
        huggingFaceMigrationMutex.withLock {
            if (huggingFaceMigrationDone) return
            val legacyToken = try {
                dataStore.data.first()[PreferencesKeys.HUGGING_FACE_TOKEN]
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.e(e, "Cannot read preferences for the HuggingFace token migration; retrying later.")
                return
            }
            if (legacyToken != null) {
                if (huggingFaceTokenFlow.value == null) {
                    secretsStore.putString(SecretKeys.HUGGING_FACE_TOKEN, legacyToken, synchronous = true)
                    huggingFaceTokenFlow.value = legacyToken
                    Timber.i("Migrated the HuggingFace token from plain DataStore to the encrypted store.")
                }
                dataStore.edit { preferences ->
                    preferences.remove(PreferencesKeys.HUGGING_FACE_TOKEN)
                }
            }
            huggingFaceMigrationDone = true
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

    override val memoryLastCompactedAt: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_LAST_COMPACTED_AT] ?: 0L
        }

    override suspend fun setMemoryLastCompactedAt(millis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEMORY_LAST_COMPACTED_AT] = millis
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
            // First provider switch ever: capture the provider the stored
            // vectors were created with, so the re-embed reminder banner can
            // compare it against the new active id. Done inside the same edit
            // so the capture and the switch land atomically.
            if (preferences[PreferencesKeys.LAST_REEMBED_PROVIDER_ID] == null) {
                preferences[PreferencesKeys.LAST_REEMBED_PROVIDER_ID] =
                    preferences[PreferencesKeys.ACTIVE_EMBEDDING_PROVIDER_ID]
                        ?: SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT
            }
            preferences[PreferencesKeys.ACTIVE_EMBEDDING_PROVIDER_ID] = id
        }
    }

    override val lastReembedProviderId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_REEMBED_PROVIDER_ID]
        }

    override suspend fun setLastReembedProviderId(id: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REEMBED_PROVIDER_ID] = id
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

    override val workspaceMaxFileSizeBytes: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.WORKSPACE_MAX_FILE_SIZE_BYTES]
                ?: SettingsDefaults.WORKSPACE_MAX_FILE_SIZE_BYTES_DEFAULT
        }

    override suspend fun setWorkspaceMaxFileSizeBytes(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WORKSPACE_MAX_FILE_SIZE_BYTES] = bytes
        }
    }

    override val workspaceMaxTotalBytes: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.WORKSPACE_MAX_TOTAL_BYTES]
                ?: SettingsDefaults.WORKSPACE_MAX_TOTAL_BYTES_DEFAULT
        }

    override suspend fun setWorkspaceMaxTotalBytes(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WORKSPACE_MAX_TOTAL_BYTES] = bytes
        }
    }

    override val workspaceReadTokenBudget: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.WORKSPACE_READ_TOKEN_BUDGET]
                ?: SettingsDefaults.WORKSPACE_READ_TOKEN_BUDGET_DEFAULT
        }

    override suspend fun setWorkspaceReadTokenBudget(tokens: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WORKSPACE_READ_TOKEN_BUDGET] = tokens
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

    override val resumeMaxAgeHours: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.RESUME_MAX_AGE_HOURS] ?: SettingsDefaults.RESUME_MAX_AGE_HOURS_DEFAULT
        }

    override suspend fun setResumeMaxAgeHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESUME_MAX_AGE_HOURS] = hours.coerceIn(
                SettingsDefaults.RESUME_MAX_AGE_HOURS_MIN,
                SettingsDefaults.RESUME_MAX_AGE_HOURS_MAX,
            )
        }
    }

    override val backgroundApprovalWindowHours: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.BACKGROUND_APPROVAL_WINDOW_HOURS]
                ?: SettingsDefaults.BACKGROUND_APPROVAL_WINDOW_HOURS_DEFAULT
        }

    override suspend fun setBackgroundApprovalWindowHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_APPROVAL_WINDOW_HOURS] = hours.coerceIn(
                SettingsDefaults.BACKGROUND_APPROVAL_WINDOW_HOURS_MIN,
                SettingsDefaults.BACKGROUND_APPROVAL_WINDOW_HOURS_MAX,
            )
        }
    }

    override val traceRetentionRunsPerSession: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TRACE_RETENTION_RUNS_PER_SESSION]
                ?: SettingsDefaults.TRACE_RETENTION_RUNS_PER_SESSION_DEFAULT
        }

    override suspend fun setTraceRetentionRunsPerSession(runs: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACE_RETENTION_RUNS_PER_SESSION] = runs.coerceIn(
                SettingsDefaults.TRACE_RETENTION_RUNS_PER_SESSION_MIN,
                SettingsDefaults.TRACE_RETENTION_RUNS_PER_SESSION_MAX,
            )
        }
    }

    override val traceRetentionMaxAgeDays: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.TRACE_RETENTION_MAX_AGE_DAYS]
                ?: SettingsDefaults.TRACE_RETENTION_MAX_AGE_DAYS_DEFAULT
        }

    override suspend fun setTraceRetentionMaxAgeDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACE_RETENTION_MAX_AGE_DAYS] = days.coerceIn(
                SettingsDefaults.TRACE_RETENTION_MAX_AGE_DAYS_MIN,
                SettingsDefaults.TRACE_RETENTION_MAX_AGE_DAYS_MAX,
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

    override val scheduledTaskNotificationsEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_TASK_NOTIFICATIONS] ?: true
        }

    override suspend fun setScheduledTaskNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_TASK_NOTIFICATIONS] = enabled
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
        /** Name of the [KeystoreBackedPrefsStore] preferences file holding settings secrets. */
        const val SECRETS_PREFS_NAME = "secure_settings_secrets"

        /** Android Keystore alias of the AEAD key dedicated to the settings-secrets store. */
        const val SECRETS_KEY_ALIAS = "knotwork.settings_secrets"

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
