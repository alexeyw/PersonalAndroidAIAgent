package ai.agent.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the numeric values exposed by [SettingsDefaults] so a silent edit to a
 * default value is caught at test time rather than at runtime by an end user.
 *
 * If a default is intentionally changed, update the asserted value in lock-step
 * with the constant: every change here is a behavioural change to the app.
 */
class SettingsDefaultsTest {

    @Test
    fun `given LLM defaults when read then match documented values`() {
        assertEquals(4_096, SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT)
        assertEquals(0.7f, SettingsDefaults.TEMPERATURE_DEFAULT)
        assertEquals(40, SettingsDefaults.TOP_K_DEFAULT)
        assertEquals(0.9f, SettingsDefaults.TOP_P_DEFAULT)
    }

    @Test
    fun `given timeout defaults when read then match documented values`() {
        assertEquals(60_000L, SettingsDefaults.TOOL_CALL_TIMEOUT_MS_DEFAULT)
        assertEquals(60_000L, SettingsDefaults.CLARIFICATION_TIMEOUT_MS_DEFAULT)
    }

    @Test
    fun `given pipeline-steps defaults when read then values bracket the default`() {
        assertEquals(15, SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT)
        assertEquals(5, SettingsDefaults.PIPELINE_MAX_STEPS_MIN)
        assertEquals(100, SettingsDefaults.PIPELINE_MAX_STEPS_MAX)
        // Sanity: the default value must lie inside the min/max window the UI
        // surfaces. Drift here would let the slider start out clamped on first
        // launch, which would silently overwrite the persisted default.
        assertTrue(
            SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT in
                SettingsDefaults.PIPELINE_MAX_STEPS_MIN..SettingsDefaults.PIPELINE_MAX_STEPS_MAX,
        )
    }

    @Test
    fun `given memory and ollama defaults when read then match documented values`() {
        assertEquals(1_000, SettingsDefaults.MEMORY_CHUNK_SEARCH_LIMIT_DEFAULT)
        assertEquals(4_096, SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT)
    }
}
