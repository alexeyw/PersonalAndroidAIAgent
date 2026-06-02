package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.grid` glyph (snap grid on) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/grid.svg`.
 */
internal val knotworkGridIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Grid")
    .strokePath("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z")
    .strokePath("M3 9h18")
    .strokePath("M3 15h18")
    .strokePath("M9 3v18")
    .strokePath("M15 3v18")
    .build()
