package ai.agent.android.presentation.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [insertAtCursor].
 *
 * The function is the bridge between the variable-chip row and any prompt editor — every
 * variation of caret/selection/edge index is exercised so chip clicks behave consistently
 * across screens.
 */
class TextFieldValueExtTest {

    @Test
    fun `given caret in middle when insert then splices and moves caret to end of insertion`() {
        val value = TextFieldValue("Hello world", selection = TextRange(5))

        val result = value.insertAtCursor(" \$DATE")

        assertEquals("Hello \$DATE world", result.text)
        assertEquals(TextRange(11), result.selection)
    }

    @Test
    fun `given selection range when insert then replaces range and moves caret`() {
        val value = TextFieldValue("Hello world", selection = TextRange(6, 11))

        val result = value.insertAtCursor("\$TIME")

        assertEquals("Hello \$TIME", result.text)
        assertEquals(TextRange(11), result.selection)
    }

    @Test
    fun `given empty text when insert then text becomes insertion`() {
        val value = TextFieldValue("", selection = TextRange.Zero)

        val result = value.insertAtCursor("\$DATE")

        assertEquals("\$DATE", result.text)
        assertEquals(TextRange(5), result.selection)
    }

    @Test
    fun `given caret at end when insert then appends`() {
        val value = TextFieldValue("Hello", selection = TextRange(5))

        val result = value.insertAtCursor(" world")

        assertEquals("Hello world", result.text)
        assertEquals(TextRange(11), result.selection)
    }

    @Test
    fun `given reversed selection when insert then replaces range correctly`() {
        // Right-to-left drag: anchor is at index 11, caret is at index 6 → start > end.
        val value = TextFieldValue("Hello world", selection = TextRange(11, 6))

        val result = value.insertAtCursor("\$TIME")

        // Selection covered "world"; replacing it should leave "Hello \$TIME" (no
        // duplication of "world" or the surrounding text).
        assertEquals("Hello \$TIME", result.text)
        assertEquals(TextRange(11), result.selection)
    }
}
