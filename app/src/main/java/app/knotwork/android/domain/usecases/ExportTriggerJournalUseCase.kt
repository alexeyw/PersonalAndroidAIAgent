package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.JournalExportDocument
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import javax.inject.Inject

/**
 * Produces the trigger-evaluation journal's export document: reads the whole
 * journal as a snapshot and renders it through [BuildTriggerJournalExportUseCase].
 *
 * **This exists to be the only seam.** The same document is produced by two very
 * different callers — the debug-only `TriggerJournalDumpReceiver` (adb, app never
 * opened, `debug` builds only) and the in-app export action that a release build
 * also has. Letting each do its own `readAll()` + format would be two
 * implementations of one format, and the second would drift from the first: a
 * dump captured during a soak run and a file a user attached to a bug report
 * would then need two parsers. They share this one call instead.
 *
 * The whole journal, never a filtered slice: the analysis that reads a dump asks
 * questions across triggers ("which day had no evaluation at all"), and a
 * per-trigger file could not answer them without the reader re-joining files by
 * hand.
 *
 * @property journal Source of the one-shot journal snapshot. Degrades to an empty
 *   list on a storage error per its observer contract, so an unreadable database
 *   yields an empty — never a failed — export.
 * @property buildExport Pure formatter turning the snapshot into the JSON document.
 */
class ExportTriggerJournalUseCase @Inject constructor(
    private val journal: TriggerJournalRepository,
    private val buildExport: BuildTriggerJournalExportUseCase,
) {

    /**
     * Reads and renders the journal.
     *
     * @param generatedAtLabel Device-local "generated at" label, pre-formatted by
     *   the caller (the domain owns no date formatting).
     * @return The JSON export document and the number of evaluations it carries.
     */
    suspend operator fun invoke(generatedAtLabel: String): JournalExportDocument {
        val evaluations = journal.readAll()
        return JournalExportDocument(
            json = buildExport(evaluations, generatedAtLabel),
            entryCount = evaluations.size,
        )
    }
}
