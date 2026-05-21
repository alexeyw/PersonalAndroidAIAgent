package ai.agent.android.data.local

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.domain.models.TestProbeResult
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.models.ToolRisk
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
import kotlinx.coroutines.flow.map
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
        val DISABLED_APP_FUNCTIONS = stringSetPreferencesKey("disabled_app_functions")
        val DISABLED_MCP_TOOLS = stringSetPreferencesKey("disabled_mcp_tools")
        val APP_FUNCTION_RISK_OVERRIDES = stringPreferencesKey("app_function_risk_overrides")
        val CURRENT_CHAT_SESSION_ID = stringPreferencesKey("current_chat_session_id")
        val MAX_MEMORY_CHUNKS_FOR_SEARCH = intPreferencesKey("max_memory_chunks_for_search")
        val LOCAL_MODEL_BACKEND = stringPreferencesKey("local_model_backend")
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
