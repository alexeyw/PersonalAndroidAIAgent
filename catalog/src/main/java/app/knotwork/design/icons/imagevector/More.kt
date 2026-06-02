package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.more` glyph (overflow ⋮ (solid)) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/more.svg`.
 */
internal val knotworkMoreIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("More")
    .fillPath("M10.6 5a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0")
    .fillPath("M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0")
    .fillPath("M10.6 19a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0")
    .build()
