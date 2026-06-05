package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.theme` glyph (theme toggle (half-filled circle)) — single-stroke icon family.
 */
internal val knotworkThemeIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Theme")
    .strokePath("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0")
    .strokePath("M12 4v16")
    .fillPath("M12 4a8 8 0 010 16z")
    .build()
