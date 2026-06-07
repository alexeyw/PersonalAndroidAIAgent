package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.globe` glyph (network / locale) — single-stroke icon family.
 */
internal val knotworkGlobeIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Globe")
    .strokePath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M3 12h18M12 3a14 14 0 010 18M12 3a14 14 0 000 18")
    .build()
