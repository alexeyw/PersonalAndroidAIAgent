package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.importFile` glyph (import a file into the workspace) — single-stroke icon family.
 */
internal val knotworkImportFileIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ImportFile")
    .strokePath("M12 3v9")
    .strokePath("M8.5 8.5l3.5 3.5l3.5 -3.5")
    .strokePath("M4 14v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1 -1v-4")
    .build()
