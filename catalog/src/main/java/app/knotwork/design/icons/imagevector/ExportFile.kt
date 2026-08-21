package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.exportFile` glyph (export a file out of the workspace) — single-stroke icon family.
 *
 * The exact mirror of [knotworkImportFileIcon]: the same tray, with the arrow
 * leaving it instead of entering. The two verbs sit on one screen (the Prompt
 * library's top-bar import action and the per-row export action), so they have
 * to read as a pair. Deliberately not `share` (that glyph means the Android
 * share sheet, and this opens a document picker) and not `save` (which reads as
 * saving inside the app).
 */
internal val knotworkExportFileIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ExportFile")
    .strokePath("M12 12V3")
    .strokePath("M8.5 6.5l3.5 -3.5l3.5 3.5")
    .strokePath("M4 14v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1 -1v-4")
    .build()
