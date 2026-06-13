package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.fileBin` glyph (binary / non-previewable file) — single-stroke icon family.
 */
internal val knotworkFileBinIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("FileBin")
    .strokePath("M6 3h8l4 4v13a1 1 0 0 1 -1 1h-11a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z")
    .strokePath("M14 3v4h4")
    .strokePath("M8.5 11h2v2h-2z")
    .strokePath("M13.5 13.5h2v2h-2z")
    .build()
