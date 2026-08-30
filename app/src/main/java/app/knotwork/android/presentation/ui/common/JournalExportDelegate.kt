package app.knotwork.android.presentation.ui.common

import app.knotwork.android.domain.models.JournalExportDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStream
import java.util.Date

/**
 * One-shot outcome of a journal export, consumed by the screen that owns the
 * action.
 *
 * Every branch is reported, including the successful ones. A journal export is a
 * silent operation by nature — the file lands somewhere the user is not looking —
 * and an action that gives no sign it happened is indistinguishable from a dead
 * control. That failure mode is not hypothetical here: this app already carries
 * one screen whose messages are emitted and never rendered.
 */
sealed interface JournalExportEvent {

    /**
     * The document is ready to hand to the system share sheet.
     *
     * @property document The rendered export.
     * @property fileName Name the receiving app should see.
     */
    data class Share(val document: JournalExportDocument, val fileName: String) : JournalExportEvent

    /**
     * The document was written to the file the user picked.
     *
     * @property entryCount How many journal rows were written. `0` is reported as
     *   such rather than as a plain success: an empty journal is a legitimate
     *   export and also the single most confusing thing to receive silently.
     */
    data class Saved(val entryCount: Int) : JournalExportEvent

    /** The document could not be written to the picked file. */
    data object SaveFailed : JournalExportEvent
}

/**
 * The export half of a journal screen's ViewModel: renders the journal on demand
 * and reports what happened.
 *
 * Shared by the trigger-evaluation journal and the external-request journal
 * rather than written twice. The two journals differ only in *which* document
 * gets built — everything around it (when the work runs, how a failure is
 * absorbed, what the screen is told) is one behaviour, and two copies of it would
 * be two chances for the screens to disagree about, say, whether an empty export
 * counts as a success.
 *
 * Shares the ViewModel's [scope] and communicates through the one-shot [events]
 * channel (`docs/architecture.md` §1.2).
 *
 * **No network anywhere on this path** — the document is rendered from the local
 * encrypted database and handed to the system share sheet or to a file the user
 * picked, both on an explicit user action. Structurally enforced by
 * `JournalExportNoNetworkKonsistTest`.
 *
 * @property scope The ViewModel's [androidx.lifecycle.viewModelScope].
 * @property fileNameStem Filename stem of this journal's export
 *   ([TRIGGER_JOURNAL_EXPORT_STEM] / [EXTERNAL_REQUESTS_EXPORT_STEM]).
 * @property buildDocument Renders the journal, given the pre-formatted
 *   "generated at" label — one of the `Export…JournalUseCase`s.
 * @property ioDispatcher Dispatcher carrying the document write; injected so a
 *   test can drive it on the test scheduler.
 */
class JournalExportDelegate(
    private val scope: CoroutineScope,
    private val fileNameStem: String,
    private val buildDocument: suspend (generatedAtLabel: String) -> JournalExportDocument,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _events = MutableSharedFlow<JournalExportEvent>(extraBufferCapacity = 1)

    /** One-shot export outcomes; the screen renders each as a share sheet or a snackbar. */
    val events: SharedFlow<JournalExportEvent> = _events.asSharedFlow()

    /**
     * Mints the filename for a new export.
     *
     * Public because the Storage Access Framework needs the name **before** the
     * document exists — the picker is opened with a suggested name, and only the
     * stream it returns is written to.
     *
     * @return The timestamped filename.
     */
    fun newFileName(): String = journalExportFileName(fileNameStem)

    /** Renders the journal and asks the screen to hand it to the share sheet. */
    fun share() {
        scope.launch {
            val now = Date()
            val document = buildDocument(journalGeneratedAtLabel(now))
            _events.tryEmit(
                JournalExportEvent.Share(
                    document = document,
                    fileName = journalExportFileName(fileNameStem, now),
                ),
            )
        }
    }

    /**
     * Renders the journal into [outputStream] — the document the user picked
     * through the Storage Access Framework — and reports the outcome.
     *
     * The stream is always closed, including on the failure path: the picker has
     * already created the file, so abandoning the stream would leave a zero-byte
     * document behind with nothing said about it.
     *
     * @param outputStream Destination document stream; owned here.
     */
    fun saveTo(outputStream: OutputStream) {
        scope.launch {
            try {
                val document = buildDocument(journalGeneratedAtLabel())
                val written = writeJournalDocument(outputStream, document.json, ioDispatcher)
                _events.tryEmit(
                    if (written) JournalExportEvent.Saved(document.entryCount) else JournalExportEvent.SaveFailed,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A journal read that threw despite its best-effort contract. The
                // stream is still open at this point, so close it before reporting.
                Timber.e(e, "Journal export failed before the document could be written")
                outputStream.close()
                _events.tryEmit(JournalExportEvent.SaveFailed)
            }
        }
    }
}
