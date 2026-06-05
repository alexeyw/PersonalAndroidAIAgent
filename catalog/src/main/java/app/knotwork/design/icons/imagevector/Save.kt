package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.save` glyph (save) — single-stroke icon family.
 */
internal val knotworkSaveIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Save")
    .strokePath("M6 4h10l4 4v11a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1z")
    .strokePath("M8 4v5h6V4")
    .strokePath("M7 20v-6h10v6")
    .build()
