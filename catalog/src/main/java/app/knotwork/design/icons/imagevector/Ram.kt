package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.ram` glyph (memory / RAM (settings)) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/ram.svg`.
 */
internal val knotworkRamIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Ram")
    .strokePath("M3 8h18a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z")
    .strokePath("M6 16v3")
    .strokePath("M9 16v3")
    .strokePath("M12 16v3")
    .strokePath("M15 16v3")
    .strokePath("M18 16v3")
    .strokePath("M6 11.5h12")
    .build()
