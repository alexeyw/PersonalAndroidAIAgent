package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.info` glyph (info (i)) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/info.svg`.
 */
internal val knotworkInfoIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Info")
    .strokePath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    .strokePath("M12 11v5.5")
    .strokePath("M12 7.6v.4")
    .build()
