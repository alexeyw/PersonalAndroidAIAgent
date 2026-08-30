package app.knotwork.android.domain.models

/**
 * A rendered journal export: the JSON document plus how many journal rows it
 * stands for.
 *
 * The count travels with the document rather than being recovered by re-reading
 * the journal or by parsing the JSON back. Both callers need it and neither can
 * cheaply re-derive it: the debug soak dump logs it as the operator's sanity
 * check that rows are accumulating at all, and the in-app export reports it back
 * to the user, where "exported 0 entries" is the one outcome that must not look
 * like a success.
 *
 * @property json The pretty-printed JSON document, ready to be written to a file.
 * @property entryCount Number of journal rows the document carries. `0` is a
 *   legitimate export of an empty journal, never an error.
 */
data class JournalExportDocument(val json: String, val entryCount: Int)
