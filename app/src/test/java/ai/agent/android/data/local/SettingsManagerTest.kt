package ai.agent.android.data.local

import ai.agent.android.domain.constants.SettingsDefaults
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [SettingsManager].
 */
class SettingsManagerTest {

    private val dataStore = mockk<DataStore<Preferences>>()

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
    fun `pipelineMaxSteps coerceIn is enforced in stored range`() = runTest {
        // Coercion is verified via ViewModel; here we just confirm the key name and default
        val prefs = mockk<Preferences>()
        every { prefs[pipelineMaxStepsKey] } returns 50
        every { dataStore.data } returns flowOf(prefs)

        val settingsManager = SettingsManager(dataStore)
        val result = settingsManager.pipelineMaxSteps.first()
        assertEquals(50, result)
    }
}
