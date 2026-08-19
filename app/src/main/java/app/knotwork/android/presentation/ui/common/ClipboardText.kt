package app.knotwork.android.presentation.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Returns the latest plain-text content of the system clipboard, or an empty
 * string when the clipboard is empty / holds no readable text.
 *
 * Shared by the "Paste" affordances that fill credential fields (the
 * HuggingFace token on the Models and Discover screens) so the clipboard-read
 * logic lives in one place.
 *
 * @param context any [Context] able to resolve the clipboard system service.
 * @return the first clip item's text as a [String], or `""` when absent.
 */
fun readPlainClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    return item?.text?.toString().orEmpty()
}

/**
 * Puts [text] on the system clipboard as plain text.
 *
 * No in-app confirmation follows: every supported Android version shows its own
 * copy confirmation, and adding a second one would say the same thing twice.
 *
 * @param context any [Context] able to resolve the clipboard system service.
 * @param label user-visible label of the clip, shown by the system UI.
 * @param text the content to copy.
 */
fun writePlainClipboardText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
