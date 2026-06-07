package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.cog` glyph (settings) — single-stroke icon family.
 */
internal val knotworkCogIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Cog")
    .strokePath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    .strokePath("M12 2v3")
    .strokePath("M12 19v3")
    .strokePath("M4.2 4.2l2.1 2.1")
    .strokePath("M17.7 17.7l2.1 2.1")
    .strokePath("M2 12h3")
    .strokePath("M19 12h3")
    .strokePath("M4.2 19.8l2.1-2.1")
    .strokePath("M17.7 6.3l2.1-2.1")
    .build()
