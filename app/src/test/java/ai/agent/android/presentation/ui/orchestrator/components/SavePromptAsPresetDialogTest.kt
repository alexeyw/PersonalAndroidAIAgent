package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.domain.constants.PromptPresetConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the parsing / submit-gate helpers backing
 * [SavePromptAsPresetDialog]. The Composable surface itself is exercised
 * via the instrumented harness; the rules are broken out into
 * [parsePromptPresetTags] / [canSavePromptPreset] so the case matrix can
 * be unit-tested without spinning up Compose.
 */
class SavePromptAsPresetDialogTest {

    @Test
    fun `parsePromptPresetTags trims and drops blanks`() {
        val tags = parsePromptPresetTags("  concise ,, reasoning,,, json ")
        assertEquals(listOf("concise", "reasoning", "json"), tags)
    }

    @Test
    fun `parsePromptPresetTags returns empty list for blank input`() {
        assertEquals(emptyList<String>(), parsePromptPresetTags("   "))
    }

    @Test
    fun `canSavePromptPreset accepts a non-blank name and prompt`() {
        assertTrue(canSavePromptPreset(name = "Concise", systemPrompt = "You are helpful."))
    }

    @Test
    fun `canSavePromptPreset rejects a blank name`() {
        assertFalse(canSavePromptPreset(name = "   ", systemPrompt = "You are helpful."))
    }

    @Test
    fun `canSavePromptPreset rejects a blank prompt`() {
        assertFalse(canSavePromptPreset(name = "Concise", systemPrompt = " "))
    }

    @Test
    fun `canSavePromptPreset rejects a name above the cap`() {
        val longName = "x".repeat(PromptPresetConstants.MAX_NAME_LENGTH + 1)
        assertFalse(canSavePromptPreset(name = longName, systemPrompt = "You are helpful."))
    }

    @Test
    fun `canSavePromptPreset accepts a name exactly at the cap`() {
        val capName = "x".repeat(PromptPresetConstants.MAX_NAME_LENGTH)
        assertTrue(canSavePromptPreset(name = capName, systemPrompt = "You are helpful."))
    }
}
