package app.knotwork.android.presentation.ui.common

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
