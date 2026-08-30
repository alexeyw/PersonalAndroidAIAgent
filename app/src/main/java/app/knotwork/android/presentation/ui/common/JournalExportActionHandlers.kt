package app.knotwork.android.presentation.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import timber.log.Timber

/**
 * The two journal-export actions a screen hands to its top bar.
 *
 * @property onShare Renders the journal and opens the system share sheet.
 * @property onSave Opens the document picker; the journal is written into
 *   whatever the user picks.
 */
class JournalExportActionHandlers(val onShare: () -> Unit, val onSave: () -> Unit)

/**
 * Wires a [JournalExportDelegate] to the platform: a Storage Access Framework
 * document picker for "save", the system share sheet for "share", and a snackbar
 * for every outcome of either.
 *
 * Written once and used by both journal screens. The alternative — each screen
 * repeating the launcher, the collector and the snackbar wording — is how two
 * screens end up telling the user different things about the same operation, and
 * how one of them ends up telling the user nothing at all.
 *
 * The picker is opened with a concrete MIME type ([JOURNAL_EXPORT_MIME]) rather
 * than the deprecated no-argument `CreateDocument()`: a wildcard type makes the
 * picker drop the `.json` extension from the name it was handed.
 *
 * @param delegate The ViewModel's export half.
 * @param snackbarHostState Host every outcome is reported on.
 * @return The handlers to pass into the screen's top-bar actions.
 */
@Composable
fun rememberJournalExportHandlers(
    delegate: JournalExportDelegate,
    snackbarHostState: SnackbarHostState,
): JournalExportActionHandlers {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.journal_export_chooser_title)

    // Outcomes are held as UiText and resolved in composition below, rather than
    // read off `Context` inside the collector: the `LocalContextGetResourceValueCall`
    // lint rule forbids that, and the count-bearing message needs a plural anyway.
    var pendingMessage by remember { mutableStateOf<UiText?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JOURNAL_EXPORT_MIME),
    ) { uri ->
        // A null uri is the user backing out of the picker. Not a failure, and
        // deliberately not reported as one.
        uri ?: return@rememberLauncherForActivityResult
        val stream = try {
            context.contentResolver.openOutputStream(uri)
        } catch (e: SecurityException) {
            // A provider that offered the document and then refused to open it.
            Timber.w(e, "Could not open the picked journal-export document")
            null
        }
        if (stream != null) {
            delegate.saveTo(stream)
        } else {
            pendingMessage = UiText(R.string.journal_export_save_failed)
        }
    }

    LaunchedEffect(delegate) {
        delegate.events.collect { event ->
            pendingMessage = when (event) {
                is JournalExportEvent.Share -> {
                    val shared = shareJournalDocument(
                        context = context,
                        json = event.document.json,
                        fileName = event.fileName,
                        chooserTitle = chooserTitle,
                    )
                    // A share sheet that opened needs no confirmation — the user is
                    // looking at it. Only its absence has to be said out loud.
                    if (shared) null else UiText(R.string.journal_export_share_failed)
                }

                is JournalExportEvent.Saved -> UiText.Plural(
                    id = R.plurals.journal_export_saved,
                    quantity = event.entryCount,
                    args = listOf(event.entryCount),
                )

                JournalExportEvent.SaveFailed -> UiText(R.string.journal_export_save_failed)
            }
        }
    }

    val message = pendingMessage?.asString()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        pendingMessage = null
    }

    return remember(delegate, saveLauncher) {
        JournalExportActionHandlers(
            onShare = delegate::share,
            onSave = { saveLauncher.launch(delegate.newFileName()) },
        )
    }
}
