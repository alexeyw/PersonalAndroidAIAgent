package app.knotwork.android.presentation.ui.chat

import android.content.Intent
import app.knotwork.android.domain.usecases.ChatExportDocument

/** MIME type of an exported chat document — the share type and the import filter. */
const val CHAT_EXPORT_MIME_JSON: String = "application/json"

/**
 * Wraps an exported chat in the system share-sheet chooser intent.
 *
 * Shared by the chat top bar and the archive row rather than built at each call
 * site: the two paths hand the user the same document, so they must also hand
 * it over the same way — a divergence here would show up as one surface
 * offering a different set of target apps than the other.
 *
 * @param chooserTitle localised chooser title.
 * @return the `ACTION_SEND` chooser intent carrying the document.
 */
fun ChatExportDocument.toShareChooser(chooserTitle: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = CHAT_EXPORT_MIME_JSON
        putExtra(Intent.EXTRA_SUBJECT, sessionName)
        putExtra(Intent.EXTRA_TEXT, json)
    }
    return Intent.createChooser(send, chooserTitle)
}
