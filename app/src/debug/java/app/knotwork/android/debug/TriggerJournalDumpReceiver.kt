package app.knotwork.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import app.knotwork.android.domain.usecases.ExportTriggerJournalUseCase
import app.knotwork.android.presentation.ui.common.TRIGGER_JOURNAL_EXPORT_STEM
import app.knotwork.android.presentation.ui.common.journalExportFileName
import app.knotwork.android.presentation.ui.common.journalGeneratedAtLabel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject

/**
 * **Debug-only** diagnostic receiver that dumps the whole trigger-evaluation
 * journal to an app-private JSON file, so a background-reliability soak run can
 * pull it off the device for offline analysis (`project_docs/metrics.md`).
 *
 * It exists solely to make the soak protocol executable without breaking its two
 * defining constraints: the app is never opened (so the in-app journal UI is off
 * limits) and the journal lives in the SQLCipher-encrypted database (so a raw
 * `adb pull` of the `.db` plus `sqlite3` cannot read it). Triggering this
 * receiver over adb writes a plaintext JSON snapshot into
 * `Android/data/<pkg>/files/soak/`, which a plain unprivileged `adb pull` can
 * retrieve on a debuggable build:
 *
 * ```
 * adb shell am broadcast \
 *   -a app.knotwork.android.debug.DUMP_TRIGGER_JOURNAL \
 *   -n app.knotwork.android/app.knotwork.android.debug.TriggerJournalDumpReceiver
 * adb pull /sdcard/Android/data/app.knotwork.android/files/soak/
 * ```
 *
 * This class is compiled only into the `debug` build type (it lives in the
 * `src/debug` source set and is registered only by the debug manifest overlay),
 * so it can never leak the journal in a release build.
 *
 * The read + format + write chain suspends and touches disk, which a
 * `BroadcastReceiver` cannot do inline: it bridges via [goAsync] and its own
 * supervisor scope, keeping the process alive until the file is written.
 */
@AndroidEntryPoint
class TriggerJournalDumpReceiver : BroadcastReceiver() {

    /**
     * Reads and renders the journal.
     *
     * The **same** call the in-app export action uses on a release build, and
     * deliberately so: one seam means a dump pulled off a soak device and a file
     * a user attached to a bug report are the identical document, readable by one
     * parser rather than two that drift.
     */
    @Inject
    lateinit var exportTriggerJournal: ExportTriggerJournalUseCase

    /**
     * Host scope of the suspending dump bridged through [goAsync]. Visible for
     * tests so they can substitute a deterministic scope.
     */
    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Reads the journal, renders it to JSON, and writes it to a timestamped file
     * under the app's external `soak/` directory. Unknown actions are ignored.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The received broadcast; only [ACTION_DUMP] is handled.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DUMP) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                dumpTo(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Trigger-journal dump failed")
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /**
     * Performs the dump: snapshot → JSON → file. Kept separate from [onReceive]
     * so the suspending body reads linearly.
     *
     * @param context Application context used to resolve the external files dir.
     */
    private suspend fun dumpTo(context: Context) {
        // Naming and header formatting are shared with the in-app export rather
        // than repeated here: a dump pulled over adb and a file the user exported
        // on a release build must be the same document, down to its header.
        val now = Date(System.currentTimeMillis())
        val document = exportTriggerJournal(journalGeneratedAtLabel(now))

        val soakDir = File(context.getExternalFilesDir(null), SOAK_DIR).apply { mkdirs() }
        val outFile = File(soakDir, journalExportFileName(TRIGGER_JOURNAL_EXPORT_STEM, now))
        outFile.writeText(document.json)
        Timber.tag(TAG).i("Trigger-journal dump: %d rows → %s", document.entryCount, outFile.absolutePath)
    }

    private companion object {
        const val TAG = "TriggerJournalDump"

        /** Broadcast action this receiver answers; matched in the debug manifest overlay. */
        const val ACTION_DUMP = "app.knotwork.android.debug.DUMP_TRIGGER_JOURNAL"

        /** External-files subdirectory the soak dumps are written into. */
        const val SOAK_DIR = "soak"
    }
}
