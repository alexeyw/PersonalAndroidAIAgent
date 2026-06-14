package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.folder` glyph (folder / Files) — single-stroke icon family.
 */
internal val knotworkFolderIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Folder")
    .strokePath("M3 6a1 1 0 0 1 1 -1h5l2 2.4h8a1 1 0 0 1 1 1v9.6a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1z")
    .build()
