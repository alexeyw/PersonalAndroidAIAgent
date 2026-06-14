package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.folderOpen` glyph (open folder / empty workspace hero) — single-stroke icon family.
 */
internal val knotworkFolderOpenIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("FolderOpen")
    .strokePath("M3 7a1 1 0 0 1 1 -1h5l2 2.4h8a1 1 0 0 1 1 1v1.6h-16")
    .strokePath("M3 11h17l-2 7a1 1 0 0 1 -1 .8h-12a1 1 0 0 1 -1 -1z")
    .build()
