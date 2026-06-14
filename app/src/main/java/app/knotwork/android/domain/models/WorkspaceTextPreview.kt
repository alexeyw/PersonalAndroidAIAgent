package app.knotwork.android.domain.models

/**
 * A bounded, read-only preview of a workspace text file for on-screen display.
 *
 * Unlike [app.knotwork.android.domain.services.AgentWorkspace.readText] — which
 * refuses a file larger than the per-file size limit with
 * [WorkspaceError.TooLarge] — a preview always returns a leading slice so the
 * Files screen can show *something* for any text file. When the file is longer
 * than the requested budget the slice is cut at a UTF-8 character boundary and
 * [truncated] is set, so the UI can show a "showing first N of M" banner and
 * steer the user toward exporting the whole file.
 *
 * @property text The leading text of the file, decoded as UTF-8. At most the
 *   requested byte budget, never splitting a multi-byte character.
 * @property totalBytes The file's full on-disk size in bytes.
 * @property truncated `true` when [text] is only a prefix of the file (the file
 *   is larger than the preview budget); `false` when [text] is the whole file.
 */
data class WorkspaceTextPreview(val text: String, val totalBytes: Long, val truncated: Boolean)
