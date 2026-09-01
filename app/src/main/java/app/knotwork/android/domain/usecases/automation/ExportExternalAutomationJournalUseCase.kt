package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.models.JournalExportDocument
import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
import javax.inject.Inject

/**
 * Produces the external-automation request journal's export document: reads the
 * whole journal as a snapshot and renders it through
 * [BuildExternalAutomationJournalExportUseCase].
 *
 * The mirror of
 * [app.knotwork.android.domain.usecases.ExportTriggerJournalUseCase], and one
 * seam for the same reason: this format has exactly one producer, so a second
 * caller added later cannot quietly grow a second shape of the same file.
 *
 * @property journal Source of the one-shot journal snapshot. Degrades to an empty
 *   list on a storage error per its observer contract, so an unreadable database
 *   yields an empty — never a failed — export.
 * @property buildExport Pure formatter turning the snapshot into the JSON document.
 */
class ExportExternalAutomationJournalUseCase @Inject constructor(
    private val journal: ExternalAutomationJournalRepository,
    private val buildExport: BuildExternalAutomationJournalExportUseCase,
) {

    /**
     * Reads and renders the journal.
     *
     * @param generatedAtLabel Device-local "generated at" label, pre-formatted by
     *   the caller (the domain owns no date formatting).
     * @return The JSON export document and the number of requests it carries.
     */
    suspend operator fun invoke(generatedAtLabel: String): JournalExportDocument {
        val entries = journal.readAll()
        return JournalExportDocument(
            json = buildExport(entries, generatedAtLabel),
            entryCount = entries.size,
        )
    }
}
