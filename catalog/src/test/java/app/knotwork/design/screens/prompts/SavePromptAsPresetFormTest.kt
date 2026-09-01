package app.knotwork.design.screens.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the parsing and submit-gate helpers behind
 * [SavePromptAsPresetDialog].
 *
 * Moved here with the dialog: the rules describe the form, and the form is now
 * a catalog component. The name cap arrives as a parameter rather than being
 * read from a constant, because the domain owns the number and the catalog must
 * not — so the cap is exercised at its boundary from both sides rather than
 * assumed.
 */
class SavePromptAsPresetFormTest {

    /** The cap the application supplies today; the helper accepts any. */
    private val maxNameLength: Int = 60

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
        assertTrue(
            canSavePromptPreset(name = "Concise", systemPrompt = "You are helpful.", maxNameLength = maxNameLength),
        )
    }

    @Test
    fun `canSavePromptPreset rejects a blank name`() {
        assertFalse(
            canSavePromptPreset(name = "   ", systemPrompt = "You are helpful.", maxNameLength = maxNameLength),
        )
    }

    @Test
    fun `canSavePromptPreset rejects a blank prompt`() {
        assertFalse(canSavePromptPreset(name = "Concise", systemPrompt = " ", maxNameLength = maxNameLength))
    }

    @Test
    fun `canSavePromptPreset rejects a name above the cap`() {
        val longName = "x".repeat(maxNameLength + 1)
        assertFalse(
            canSavePromptPreset(name = longName, systemPrompt = "You are helpful.", maxNameLength = maxNameLength),
        )
    }

    @Test
    fun `canSavePromptPreset accepts a name exactly at the cap`() {
        val capName = "x".repeat(maxNameLength)
        assertTrue(
            canSavePromptPreset(name = capName, systemPrompt = "You are helpful.", maxNameLength = maxNameLength),
        )
    }
}
