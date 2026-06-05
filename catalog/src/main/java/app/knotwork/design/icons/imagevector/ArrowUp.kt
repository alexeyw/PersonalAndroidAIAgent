package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowUp` glyph (collapse ▲ (chevron, pairs arrowDown)) — single-stroke icon family.
 */
internal val knotworkArrowUpIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowUp")
    .strokePath("M6 15l6-6 6 6")
    .build()
