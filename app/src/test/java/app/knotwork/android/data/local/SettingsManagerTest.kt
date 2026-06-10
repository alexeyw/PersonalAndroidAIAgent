package app.knotwork.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.InMemorySharedPreferences
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTransport
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.models.UpdateMcpServerResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Unit tests for [SettingsManager].
 */
class SettingsManagerTest {

    private val dataStore = mockk<DataStore<Preferences>>()

    /**
     * Fakes backing the Keystore-backed secrets store (HuggingFace token):
     * an in-memory prefs file plus a deterministic AEAD cipher, wired
     * through a relaxed context mock shared by every construction below.
     */
    private val securePrefs = InMemorySharedPreferences()
    private val cipher = FakeAeadCipher()
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences("secure_settings_secrets", Context.MODE_PRIVATE) } returns securePrefs
    }

    /**
     * Backs the `updateMcpServer` write-path integration tests below with a
     * **real** file-backed `PreferenceDataStore` so the assertions can
     * round-trip through `dataStore.edit { … }` and observe the persisted
     * state. The existing read-only tests (above) keep their lighter mock-
     * based pattern; mocking the `edit { … }` extension is hairy in mockk
     * and a temp-file DataStore is the most faithful integration target.
     */
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // private val settingsManager = SettingsManager(dataStore, context, cipher)
    private val isFirstLaunchKey = booleanPreferencesKey("is_first_launch")
    private val temperatureKey = androidx.datastore.preferences.core.floatPreferencesKey("temperature")
    private val topKKey = androidx.datastore.preferences.core.intPreferencesKey("top_k")
    private val topPKey = androidx.datastore.preferences.core.floatPreferencesKey("top_p")
    private val requiresUserConfirmationKey = booleanPreferencesKey("requires_user_confirmation")
    private val lastReembedProviderIdKey = stringPreferencesKey("last_reembed_provider_id")
    private val pipelineMaxStepsKey = androidx.datastore.preferences.core.intPreferencesKey("pipeline_max_steps")
    private val crashReportingEnabledKey = booleanPreferencesKey("crash_reporting_enabled")
    private val appFunctionRiskOverridesKey = stringPreferencesKey("app_function_risk_overrides")
    private val hasCompletedOnboardingKey = booleanPreferencesKey("has_completed_onboarding")
    private val activeEmbeddingProviderIdKey = stringPreferencesKey("active_embedding_provider_id")
    private val memorySearchTopKKey = androidx.datastore.preferences.core.intPreferencesKey("memory_search_top_k")
    private val memorySearchThresholdKey =
        androidx.datastore.preferences.core.floatPreferencesKey("memory_search_threshold")
    private val memoryCompactionEnabledKey = booleanPreferencesKey("memory_compaction_enabled")
    private val verboseMemoryLoggingEnabledKey = booleanPreferencesKey("verbose_memory_logging_enabled")
    private val memoryCompactionAgeDaysKey =
        androidx.datastore.preferences.core.intPreferencesKey("memory_compaction_age_days")
    private val maxMemoryChunksKey = androidx.datastore.preferences.core.intPreferencesKey("max_memory_chunks")

    @Test
    fun `isFirstLaunch returns true by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[isFirstLaunchKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.isFirstLaunch.first()
        assertTrue(result)
    }

    @Test
    fun `hasCompletedOnboarding returns false by default`() = runTest {
        // The flag must default to `false` so the onboarding gate trips on
        // fresh installs — the canonical "user has not seen onboarding yet"
        // signal. Confusion with `isFirstLaunch` (which defaults to `true`
        // and is cleared by `InitializeAppUseCase`) is exactly what this
        // separate flag exists to prevent.
        val prefs = mockk<Preferences>()
        every { prefs[hasCompletedOnboardingKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.hasCompletedOnboarding.first()
        org.junit.Assert.assertFalse(result)
    }

    @Test
    fun `temperature returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[temperatureKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.temperature.first()
        assertEquals(SettingsDefaults.TEMPERATURE_DEFAULT, result)
    }

    @Test
    fun `topK returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[topKKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.topK.first()
        assertEquals(SettingsDefaults.TOP_K_DEFAULT, result)
    }

    @Test
    fun `topP returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[topPKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.topP.first()
        assertEquals(SettingsDefaults.TOP_P_DEFAULT, result)
    }

    @Test
    fun `requiresUserConfirmation returns true by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[requiresUserConfirmationKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.requiresUserConfirmation.first()
        assertTrue(result)
    }

    @Test
    fun `lastReembedProviderId returns null by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[lastReembedProviderIdKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        assertNull(settingsManager.lastReembedProviderId.first())
    }

    @Test
    fun `setLastReembedProviderId persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setLastReembedProviderId("ollama")
            assertEquals("ollama", manager.lastReembedProviderId.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `setActiveEmbeddingProviderId captures the previous provider as baseline on first switch only`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            // Given — a fresh install: the default provider is active and no
            // baseline has ever been captured.
            assertNull(manager.lastReembedProviderId.first())

            // When — the user switches providers for the first time.
            manager.setActiveEmbeddingProviderId("ollama")

            // Then — the provider the stored vectors were created with (the
            // default) is captured as the baseline.
            assertEquals("ollama", manager.activeEmbeddingProviderId.first())
            assertEquals(
                SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT,
                manager.lastReembedProviderId.first(),
            )

            // When — a second switch happens without a re-embed in between.
            manager.setActiveEmbeddingProviderId("openai_3_small")

            // Then — the baseline is NOT overwritten (the vectors are still in
            // the original provider's space).
            assertEquals(
                SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT,
                manager.lastReembedProviderId.first(),
            )

            // When — the user switches back to the baseline provider.
            manager.setActiveEmbeddingProviderId(SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT)

            // Then — active equals baseline again, so the mismatch banner
            // condition no longer holds.
            assertEquals(manager.lastReembedProviderId.first(), manager.activeEmbeddingProviderId.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `memorySearchTopK returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[memorySearchTopKKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.memorySearchTopK.first()
        assertEquals(SettingsDefaults.MEMORY_SEARCH_TOP_K_DEFAULT, result)
    }

    @Test
    fun `memorySearchThreshold returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[memorySearchThresholdKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.memorySearchThreshold.first()
        assertEquals(SettingsDefaults.MEMORY_SEARCH_THRESHOLD_DEFAULT, result)
    }

    @Test
    fun `setMemorySearchTopK persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setMemorySearchTopK(12)
            assertEquals(12, manager.memorySearchTopK.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `setMemorySearchThreshold persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setMemorySearchThreshold(0.72f)
            assertEquals(0.72f, manager.memorySearchThreshold.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `memoryCompactionEnabled returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[memoryCompactionEnabledKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.memoryCompactionEnabled.first()
        assertEquals(SettingsDefaults.MEMORY_COMPACTION_ENABLED_DEFAULT, result)
    }

    @Test
    fun `setMemoryCompactionEnabled persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setMemoryCompactionEnabled(false)
            assertEquals(false, manager.memoryCompactionEnabled.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `verboseMemoryLoggingEnabled returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[verboseMemoryLoggingEnabledKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.verboseMemoryLoggingEnabled.first()
        assertEquals(SettingsDefaults.VERBOSE_MEMORY_LOGGING_ENABLED_DEFAULT, result)
    }

    @Test
    fun `setVerboseMemoryLoggingEnabled persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setVerboseMemoryLoggingEnabled(true)
            assertEquals(true, manager.verboseMemoryLoggingEnabled.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `memoryCompactionAgeDays returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[memoryCompactionAgeDaysKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.memoryCompactionAgeDays.first()
        assertEquals(SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_DEFAULT, result)
    }

    @Test
    fun `setMemoryCompactionAgeDays persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setMemoryCompactionAgeDays(45)
            assertEquals(45, manager.memoryCompactionAgeDays.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `maxMemoryChunks returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[maxMemoryChunksKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.maxMemoryChunks.first()
        assertEquals(SettingsDefaults.MAX_MEMORY_CHUNKS_DEFAULT, result)
    }

    @Test
    fun `setMaxMemoryChunks persists and is read back`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.setMaxMemoryChunks(8000)
            assertEquals(8000, manager.maxMemoryChunks.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `isFirstLaunch returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[isFirstLaunchKey] } returns false
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.isFirstLaunch.first()
        assertEquals(false, result)
    }

    @Test
    fun `isFirstLaunch handles IOException and returns default`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.isFirstLaunch.first()
        assertTrue(result)
    }

    @Test
    fun `pipelineMaxSteps returns default value of 15`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(15, result)
    }

    @Test
    fun `pipelineMaxSteps returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns 30
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(30, result)
    }

    @Test
    fun `crashReportingEnabled returns false by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[crashReportingEnabledKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.crashReportingEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun `crashReportingEnabled returns stored true value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[crashReportingEnabledKey] } returns true
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.crashReportingEnabled.first()
        assertTrue(result)
    }

    @Test
    fun `crashReportingEnabled handles IOException and falls back to false`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.crashReportingEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun `appFunctionRiskOverrides returns empty map when nothing is stored`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `appFunctionRiskOverrides parses stored JSON map into typed risks`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns
            "{\"echo\":\"READ_ONLY\",\"send_email\":\"DESTRUCTIVE\"}"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.appFunctionRiskOverrides.first()

        assertEquals(2, result.size)
        assertEquals(ToolRisk.READ_ONLY, result["echo"])
        assertEquals(ToolRisk.DESTRUCTIVE, result["send_email"])
    }

    @Test
    fun `appFunctionRiskOverrides drops entries with unknown risk values`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns
            "{\"echo\":\"READ_ONLY\",\"bogus\":\"NOT_A_REAL_RISK\"}"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.appFunctionRiskOverrides.first()

        assertEquals(1, result.size)
        assertEquals(ToolRisk.READ_ONLY, result["echo"])
        assertTrue(!result.containsKey("bogus"))
    }

    @Test
    fun `appFunctionRiskOverrides returns empty map on malformed JSON`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns "this is not json"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `appFunctionRiskOverrides handles IOException and falls back to empty map`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipelineMaxSteps coerceIn is enforced in stored range`() = runTest {
        // Coercion is verified via ViewModel; here we just confirm the key name and default
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns 50
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(50, result)
    }

    @Test
    fun `mcpServers migrates legacy MCP_SERVER_URLS stringSet to default configs`() = runTest {
        // The MCP persistence expanded from a stringSet of URLs to a
        // JSON-encoded List<McpServerConfig>. Existing installs hold the old key — the
        // manager must surface them as default configs (no headers, SSE transport)
        // until the first write replaces the storage shape.
        val legacyKey = stringSetPreferencesKey("mcp_server_urls")
        val newJsonKey = stringPreferencesKey("mcp_servers_json")
        val prefs = mockk<Preferences>()
        every { prefs[legacyKey] } returns setOf("https://legacy.example/mcp")
        every { prefs[newJsonKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val result = SettingsManager(dataStore, context, cipher).mcpServers.first()

        assertEquals(1, result.size)
        assertEquals("https://legacy.example/mcp", result[0].url)
        assertEquals(null, result[0].name)
        assertEquals(McpTransport.SSE, result[0].transport)
        assertTrue(result[0].headers.isEmpty())
    }

    @Test
    fun `mcpServers decodes JSON-encoded list with headers and transport`() = runTest {
        val legacyKey = stringSetPreferencesKey("mcp_server_urls")
        val newJsonKey = stringPreferencesKey("mcp_servers_json")
        val prefs = mockk<Preferences>()
        every { prefs[legacyKey] } returns null
        every {
            prefs[newJsonKey]
        } returns """
            [
              {
                "url":"https://hf.example/mcp",
                "name":"HuggingFace",
                "transport":"streamable_http",
                "headers":{"Authorization":"Bearer secret"}
              }
            ]
        """.trimIndent()
        every { dataStore.data } returns flowOf(prefs)

        val result = SettingsManager(dataStore, context, cipher).mcpServers.first()

        assertEquals(1, result.size)
        assertEquals("HuggingFace", result[0].name)
        assertEquals(McpTransport.STREAMABLE_HTTP, result[0].transport)
        assertEquals("Bearer secret", result[0].headers["Authorization"])
    }

    @Test
    fun `mcpServers decodes typed Bearer auth payload`() = runTest {
        val newJsonKey = stringPreferencesKey("mcp_servers_json")
        val prefs = mockk<Preferences>()
        every { prefs[stringSetPreferencesKey("mcp_server_urls")] } returns null
        every {
            prefs[newJsonKey]
        } returns """
            [
              {
                "url":"https://hf.example/mcp",
                "auth":{"type":"bearer","token":"abc"}
              }
            ]
        """.trimIndent()
        every { dataStore.data } returns flowOf(prefs)

        val result = SettingsManager(dataStore, context, cipher).mcpServers.first()

        assertEquals(McpAuth.Bearer(token = "abc"), result.single().auth)
    }

    @Test
    fun `mcpServers decodes typed ApiKey auth payload`() = runTest {
        val newJsonKey = stringPreferencesKey("mcp_servers_json")
        val prefs = mockk<Preferences>()
        every { prefs[stringSetPreferencesKey("mcp_server_urls")] } returns null
        every {
            prefs[newJsonKey]
        } returns """
            [
              {
                "url":"https://api.example/mcp",
                "auth":{"type":"apiKey","headerName":"X-API-Key","value":"v1"}
              }
            ]
        """.trimIndent()
        every { dataStore.data } returns flowOf(prefs)

        val result = SettingsManager(dataStore, context, cipher).mcpServers.first()

        assertEquals(McpAuth.ApiKey(headerName = "X-API-Key", value = "v1"), result.single().auth)
    }

    // ───────────────────────────────────────────────────────────────────
    // HuggingFace token: encrypted storage + legacy-DataStore migration
    // (real PreferenceDataStore, same pattern as the MCP tests below).
    // ───────────────────────────────────────────────────────────────────

    private val huggingFaceTokenKey = stringPreferencesKey("hugging_face_token")

    /** Like [freshManagerWithRealDataStore] but also exposes the backing DataStore. */
    private fun freshManagerWithExposedDataStore(): Triple<SettingsManager, DataStore<Preferences>, CoroutineScope> {
        val file = tempFolder.newFile("settings-manager-hf-${System.nanoTime()}.preferences_pb")
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return Triple(SettingsManager(ds, context, cipher), ds, scope)
    }

    @Test
    fun `huggingFace token round-trips through the encrypted store across instances`() = runTest {
        val (manager, ds, scope) = freshManagerWithExposedDataStore()
        try {
            manager.setHuggingFaceAuthToken("hf_secret_token")

            val fresh = SettingsManager(ds, context, cipher)
            assertEquals("hf_secret_token", fresh.huggingFaceAuthToken.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `huggingFace token is not stored as plaintext`() = runTest {
        val (manager, _, scope) = freshManagerWithExposedDataStore()
        try {
            manager.setHuggingFaceAuthToken("hf_secret_token")

            val raw = securePrefs.values["hugging_face_token"] as String
            assertTrue(!raw.contains("hf_secret_token"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `legacy DataStore token migrates to the encrypted store on first read`() = runTest {
        val (manager, ds, scope) = freshManagerWithExposedDataStore()
        try {
            ds.edit { it[huggingFaceTokenKey] = "hf_legacy_token" }

            val token = manager.huggingFaceAuthToken.first()

            assertEquals("hf_legacy_token", token)
            // The plaintext copy is gone from DataStore…
            assertNull(ds.data.first()[huggingFaceTokenKey])
            // …and the encrypted store now holds it (not in plaintext).
            val raw = securePrefs.values["hugging_face_token"] as String
            assertTrue(!raw.contains("hf_legacy_token"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `migration does not overwrite an already-encrypted token`() = runTest {
        val (manager, ds, scope) = freshManagerWithExposedDataStore()
        try {
            manager.setHuggingFaceAuthToken("hf_current_token")
            // Simulate a stale plaintext leftover from a crashed earlier migration.
            ds.edit { it[huggingFaceTokenKey] = "hf_stale_legacy" }
            val fresh = SettingsManager(ds, context, cipher)

            val token = fresh.huggingFaceAuthToken.first()

            assertEquals("hf_current_token", token)
            assertNull(ds.data.first()[huggingFaceTokenKey])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `setting token to null removes the encrypted entry`() = runTest {
        val (manager, _, scope) = freshManagerWithExposedDataStore()
        try {
            manager.setHuggingFaceAuthToken("hf_secret_token")

            manager.setHuggingFaceAuthToken(null)

            assertNull(manager.huggingFaceAuthToken.first())
            assertTrue(!securePrefs.values.containsKey("hugging_face_token"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `undecryptable stored token is treated as unset and dropped`() = runTest {
        val (manager, ds, scope) = freshManagerWithExposedDataStore()
        try {
            manager.setHuggingFaceAuthToken("hf_secret_token")
            cipher.failDecrypt = true
            val fresh = SettingsManager(ds, context, cipher)

            // A lost Keystore key must surface as "no token configured":
            // the token is user re-enterable, same policy as API keys.
            assertNull(fresh.huggingFaceAuthToken.first())
            assertTrue(!securePrefs.values.containsKey("hugging_face_token"))
        } finally {
            scope.cancel()
        }
    }

    // ───────────────────────────────────────────────────────────────────
    // updateMcpServer integration tests (real PreferenceDataStore).
    //
    // The collision-detection logic itself is pure-tested in
    // McpServerCollisionCheckTest; the cases below verify that the
    // SettingsManager method actually dispatches to it AND skips the
    // dataStore.edit { … } write when a collision is detected — the part
    // that matters for "persists nothing" semantics.
    // ───────────────────────────────────────────────────────────────────

    private fun freshManagerWithRealDataStore(): Pair<SettingsManager, CoroutineScope> {
        val file = tempFolder.newFile("settings-manager-mcp-${System.nanoTime()}.preferences_pb")
        // Files created by JUnit's TemporaryFolder start as empty 0-byte files,
        // which DataStore would treat as a corrupt preferences blob and throw on
        // first read. Delete the file so DataStore can create it from scratch
        // on the first write.
        file.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return SettingsManager(ds, context, cipher) to scope
    }

    @Test
    fun `updateMcpServer returns UrlCollision when new url matches another existing row`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.addMcpServer(McpServerConfig(url = "http://a", name = "Server A"))
            manager.addMcpServer(McpServerConfig(url = "http://b", name = "Server B"))

            val result = manager.updateMcpServer(
                originalUrl = "http://a",
                updated = McpServerConfig(url = "http://b", name = "About to overwrite B"),
            )

            // Typed result carries the colliding row's identity for the UI.
            assertTrue(
                "Expected UrlCollision, got $result",
                result is UpdateMcpServerResult.UrlCollision,
            )
            val collision = result as UpdateMcpServerResult.UrlCollision
            assertEquals("http://b", collision.collidingUrl)
            assertEquals("Server B", collision.collidingDisplayName)

            // Persistence MUST be untouched — A still has its original name,
            // B still has its original name, no duplicate row sneaked in.
            val onDisk = manager.mcpServers.first()
            assertEquals(2, onDisk.size)
            assertEquals("http://a", onDisk[0].url)
            assertEquals("Server A", onDisk[0].name)
            assertEquals("http://b", onDisk[1].url)
            assertEquals("Server B", onDisk[1].name)
            assertNotEquals("About to overwrite B", onDisk[1].name)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `updateMcpServer succeeds when new url matches its own current url (no-op edit)`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.addMcpServer(McpServerConfig(url = "http://a", name = "Original"))

            // Same URL, renamed display label — the canonical "rename only" edit
            // path. Must not trip the collision guard.
            val result = manager.updateMcpServer(
                originalUrl = "http://a",
                updated = McpServerConfig(url = "http://a", name = "Renamed"),
            )

            assertEquals(UpdateMcpServerResult.Success, result)
            val onDisk = manager.mcpServers.first()
            assertEquals(1, onDisk.size)
            assertEquals("http://a", onDisk[0].url)
            assertEquals("Renamed", onDisk[0].name)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `updateMcpServer succeeds and persists when the new url is unique`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            manager.addMcpServer(McpServerConfig(url = "http://a", name = "Server A"))

            val result = manager.updateMcpServer(
                originalUrl = "http://a",
                updated = McpServerConfig(url = "http://c", name = "Server C"),
            )

            assertEquals(UpdateMcpServerResult.Success, result)
            val onDisk = manager.mcpServers.first()
            assertEquals(1, onDisk.size)
            assertEquals("http://c", onDisk[0].url)
            assertEquals("Server C", onDisk[0].name)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `activeEmbeddingProviderId returns on-device default when nothing stored`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[activeEmbeddingProviderIdKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.activeEmbeddingProviderId.first()

        assertEquals(SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT, result)
        assertEquals("use", result)
    }

    @Test
    fun `activeEmbeddingProviderId returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[activeEmbeddingProviderIdKey] } returns "openai_3_small"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.activeEmbeddingProviderId.first()

        assertEquals("openai_3_small", result)
    }

    @Test
    fun `activeEmbeddingProviderId handles IOException and returns default`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore, context, cipher)
        val result = settingsManager.activeEmbeddingProviderId.first()

        assertEquals(SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT, result)
    }

    @Test
    fun `setActiveEmbeddingProviderId persists and round-trips through DataStore`() = runTest {
        val (manager, scope) = freshManagerWithRealDataStore()
        try {
            // Default before any write.
            assertEquals("use", manager.activeEmbeddingProviderId.first())

            manager.setActiveEmbeddingProviderId("ollama")

            assertEquals("ollama", manager.activeEmbeddingProviderId.first())
        } finally {
            scope.cancel()
        }
    }
}
