package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.fileText` glyph (text / previewable file) — single-stroke icon family.
 */
internal val knotworkFileTextIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("FileText")
    .strokePath("M6 3h8l4 4v13a1 1 0 0 1 -1 1h-11a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z")
    .strokePath("M14 3v4h4")
    .strokePath("M8.5 12.5h7")
    .strokePath("M8.5 16h7")
    .strokePath("M8.5 9h2.5")
    .build()
