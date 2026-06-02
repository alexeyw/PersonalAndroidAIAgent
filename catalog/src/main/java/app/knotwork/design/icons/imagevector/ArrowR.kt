package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowR` glyph (forward / expand →) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/arrowR.svg`.
 */
internal val knotworkArrowRIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowR")
    .strokePath("M5 12h14M13 6l6 6-6 6")
    .build()
