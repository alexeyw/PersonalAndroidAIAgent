package ai.agent.android.data.local

import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.McpAuth
import ai.agent.android.domain.models.McpServerConfig
import ai.agent.android.domain.models.McpTransport
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.models.UpdateMcpServerResult
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
     * Backs the `updateMcpServer` write-path integration tests below with a
     * **real** file-backed `PreferenceDataStore` so the assertions can
     * round-trip through `dataStore.edit { … }` and observe the persisted
     * state. The existing read-only tests (above) keep their lighter mock-
     * based pattern; mocking the `edit { … }` extension is hairy in mockk
     * and a temp-file DataStore is the most faithful integration target.
     */
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // private val settingsManager = SettingsManager(dataStore)
    private val isFirstLaunchKey = booleanPreferencesKey("is_first_launch")
    private val temperatureKey = androidx.datastore.preferences.core.floatPreferencesKey("temperature")
    private val topKKey = androidx.datastore.preferences.core.intPreferencesKey("top_k")
    private val topPKey = androidx.datastore.preferences.core.floatPreferencesKey("top_p")
    private val requiresUserConfirmationKey = booleanPreferencesKey("requires_user_confirmation")
    private val maxMemoryChunksForSearchKey = androidx.datastore.preferences.core.intPreferencesKey(
        "max_memory_chunks_for_search",
    )
    private val pipelineMaxStepsKey = androidx.datastore.preferences.core.intPreferencesKey("pipeline_max_steps")
    private val crashReportingEnabledKey = booleanPreferencesKey("crash_reporting_enabled")
    private val appFunctionRiskOverridesKey = stringPreferencesKey("app_function_risk_overrides")
    private val hasCompletedOnboardingKey = booleanPreferencesKey("has_completed_onboarding")
    private val activeEmbeddingProviderIdKey = stringPreferencesKey("active_embedding_provider_id")

    @Test
    fun `isFirstLaunch returns true by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[isFirstLaunchKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
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

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.hasCompletedOnboarding.first()
        org.junit.Assert.assertFalse(result)
    }

    @Test
    fun `temperature returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[temperatureKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.temperature.first()
        assertEquals(SettingsDefaults.TEMPERATURE_DEFAULT, result)
    }

    @Test
    fun `topK returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[topKKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.topK.first()
        assertEquals(SettingsDefaults.TOP_K_DEFAULT, result)
    }

    @Test
    fun `topP returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[topPKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.topP.first()
        assertEquals(SettingsDefaults.TOP_P_DEFAULT, result)
    }

    @Test
    fun `requiresUserConfirmation returns true by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[requiresUserConfirmationKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.requiresUserConfirmation.first()
        assertTrue(result)
    }

    @Test
    fun `maxMemoryChunksForSearch returns default value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[maxMemoryChunksForSearchKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.maxMemoryChunksForSearch.first()
        assertEquals(1000, result)
    }

    @Test
    fun `isFirstLaunch returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[isFirstLaunchKey] } returns false
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.isFirstLaunch.first()
        assertEquals(false, result)
    }

    @Test
    fun `isFirstLaunch handles IOException and returns default`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.isFirstLaunch.first()
        assertTrue(result)
    }

    @Test
    fun `pipelineMaxSteps returns default value of 15`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(15, result)
    }

    @Test
    fun `pipelineMaxSteps returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns 30
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(30, result)
    }

    @Test
    fun `crashReportingEnabled returns false by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[crashReportingEnabledKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.crashReportingEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun `crashReportingEnabled returns stored true value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[crashReportingEnabledKey] } returns true
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.crashReportingEnabled.first()
        assertTrue(result)
    }

    @Test
    fun `crashReportingEnabled handles IOException and falls back to false`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.crashReportingEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun `appFunctionRiskOverrides returns empty map when nothing is stored`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `appFunctionRiskOverrides parses stored JSON map into typed risks`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[appFunctionRiskOverridesKey] } returns
            "{\"echo\":\"READ_ONLY\",\"send_email\":\"DESTRUCTIVE\"}"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
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

        val settingsManager = SettingsManager(dataStore)
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

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `appFunctionRiskOverrides handles IOException and falls back to empty map`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.appFunctionRiskOverrides.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipelineMaxSteps coerceIn is enforced in stored range`() = runTest {
        // Coercion is verified via ViewModel; here we just confirm the key name and default
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns 50
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(50, result)
    }

    @Test
    fun `mcpServers migrates legacy MCP_SERVER_URLS stringSet to default configs`() = runTest {
        // Phase 22 / Task 10 expanded the MCP persistence from a stringSet of URLs to a
        // JSON-encoded List<McpServerConfig>. Existing installs hold the old key — the
        // manager must surface them as default configs (no headers, SSE transport)
        // until the first write replaces the storage shape.
        val legacyKey = stringSetPreferencesKey("mcp_server_urls")
        val newJsonKey = stringPreferencesKey("mcp_servers_json")
        val prefs = mockk<Preferences>()
        every { prefs[legacyKey] } returns setOf("https://legacy.example/mcp")
        every { prefs[newJsonKey] } returns null
        every { dataStore.data } returns flowOf(prefs)

        val result = SettingsManager(dataStore).mcpServers.first()

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

        val result = SettingsManager(dataStore).mcpServers.first()

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

        val result = SettingsManager(dataStore).mcpServers.first()

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

        val result = SettingsManager(dataStore).mcpServers.first()

        assertEquals(McpAuth.ApiKey(headerName = "X-API-Key", value = "v1"), result.single().auth)
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
        return SettingsManager(ds) to scope
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

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.activeEmbeddingProviderId.first()

        assertEquals(SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT, result)
        assertEquals("use", result)
    }

    @Test
    fun `activeEmbeddingProviderId returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[activeEmbeddingProviderIdKey] } returns "openai_3_small"
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.activeEmbeddingProviderId.first()

        assertEquals("openai_3_small", result)
    }

    @Test
    fun `activeEmbeddingProviderId handles IOException and returns default`() = runTest {
        every { dataStore.data } returns flow { throw IOException("Test") }

        val settingsManager = SettingsManager(dataStore)
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
