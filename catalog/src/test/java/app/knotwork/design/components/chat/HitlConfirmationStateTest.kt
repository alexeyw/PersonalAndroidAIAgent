package app.knotwork.design.components.chat

import app.knotwork.design.components.chips.Risk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [HitlConfirmationState] — the gating logic behind
 * [HitlConfirmationCard]'s Allow CTA, Always-Allow visibility, and
 * destructive typed-confirm row.
 *
 * Keeps the rules unit-testable without Robolectric / Compose so the
 * behaviour survives refactors of the Composable itself.
 */
class HitlConfirmationStateTest {

    @Test
    fun `given readonly when isAllowOnceEnabled then returns true regardless of typed value`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Readonly, ""))
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Readonly, "no"))
    }

    @Test
    fun `given sensitive when isAllowOnceEnabled then returns true regardless of typed value`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Sensitive, ""))
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Sensitive, "anything"))
    }

    @Test
    fun `given destructive and empty typed when isAllowOnceEnabled then returns false`() {
        assertFalse(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, ""))
    }

    @Test
    fun `given destructive and partial typed when isAllowOnceEnabled then returns false`() {
        assertFalse(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "ye"))
    }

    @Test
    fun `given destructive and exact match when isAllowOnceEnabled then returns true`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "yes"))
    }

    @Test
    fun `given destructive and uppercase match when isAllowOnceEnabled then returns true`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "YES"))
    }

    @Test
    fun `given destructive and padded match when isAllowOnceEnabled then trims and returns true`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "  yes  "))
    }

    @Test
    fun `given destructive and non-matching word when isAllowOnceEnabled then returns false`() {
        assertFalse(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "yeah"))
        assertFalse(HitlConfirmationState.isAllowOnceEnabled(Risk.Destructive, "no"))
    }

    @Test
    fun `showAlwaysAllow is true only for sensitive`() {
        assertFalse(HitlConfirmationState.showAlwaysAllow(Risk.Readonly))
        assertTrue(HitlConfirmationState.showAlwaysAllow(Risk.Sensitive))
        assertFalse(HitlConfirmationState.showAlwaysAllow(Risk.Destructive))
    }

    @Test
    fun `showTypedConfirmRow is true only for destructive`() {
        assertFalse(HitlConfirmationState.showTypedConfirmRow(Risk.Readonly))
        assertFalse(HitlConfirmationState.showTypedConfirmRow(Risk.Sensitive))
        assertTrue(HitlConfirmationState.showTypedConfirmRow(Risk.Destructive))
    }

    @Test
    fun `confirm word is canonical lowercase`() {
        assertEquals("yes", HitlConfirmationState.DESTRUCTIVE_CONFIRM_WORD)
    }
}
