package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowUpLine` glyph (up / scroll-to-top (straight)) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/arrowUpLine.svg`.
 */
internal val knotworkArrowUpLineIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowUpLine")
    .strokePath("M12 20V5")
    .strokePath("M6 11l6-6 6 6")
    .build()
