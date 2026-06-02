package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowUp` glyph (collapse ▲ (chevron, pairs arrowDown)) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/arrowUp.svg`.
 */
internal val knotworkArrowUpIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowUp")
    .strokePath("M6 15l6-6 6 6")
    .build()
