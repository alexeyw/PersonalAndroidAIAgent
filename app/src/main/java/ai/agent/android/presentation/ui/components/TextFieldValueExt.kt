package ai.agent.android.presentation.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Splices [insertion] at the current selection of this [TextFieldValue]:
 *  - replaces any active selection range with [insertion];
 *  - when the selection is a caret, inserts at the caret;
 *  - moves the caret to the end of the inserted text so the user can keep typing.
 *
 * Uses [androidx.compose.ui.text.TextRange.min]/[androidx.compose.ui.text.TextRange.max]
 * (not start/end) because Compose preserves anchor direction inside [TextRange]: a
 * right-to-left drag yields `start > end`, so naïve start/end slicing would insert at
 * the wrong offset and duplicate the selected text. min/max are direction-agnostic.
 *
 * Used by every prompt editor that hosts the [VariableChipsRow] so chip clicks splice
 * `$VARIABLE` tokens at the user's current cursor position.
 */
fun TextFieldValue.insertAtCursor(insertion: String): TextFieldValue {
    val from = selection.min
    val to = selection.max
    val newText = text.substring(0, from) + insertion + text.substring(to)
    val caret = from + insertion.length
    return copy(text = newText, selection = TextRange(caret))
}
