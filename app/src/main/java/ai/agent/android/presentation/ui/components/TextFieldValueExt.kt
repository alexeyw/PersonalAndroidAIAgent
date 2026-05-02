package ai.agent.android.presentation.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Splices [insertion] at the current selection of this [TextFieldValue]:
 *  - replaces any active selection range with [insertion];
 *  - when the selection is a caret, inserts at the caret;
 *  - moves the caret to the end of the inserted text so the user can keep typing.
 *
 * Used by every prompt editor that hosts the [VariableChipsRow] so chip clicks splice
 * `$VARIABLE` tokens at the user's current cursor position.
 */
fun TextFieldValue.insertAtCursor(insertion: String): TextFieldValue {
    val start = selection.start
    val end = selection.end
    val newText = text.substring(0, start) + insertion + text.substring(end)
    val caret = start + insertion.length
    return copy(text = newText, selection = TextRange(caret))
}
