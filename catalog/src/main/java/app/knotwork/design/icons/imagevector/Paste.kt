package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.paste` glyph (paste (clipboard)) — single-stroke icon family.
 */
internal val knotworkPasteIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Paste")
    .strokePath("M6 5h12a1 1 0 011 1v13a1 1 0 01-1 1H6a1 1 0 01-1-1V6a1 1 0 011-1z")
    .strokePath("M9 5V4a1 1 0 011-1h4a1 1 0 011 1v1z")
    .build()
