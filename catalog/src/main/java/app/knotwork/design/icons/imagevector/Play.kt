package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.play` glyph (run (solid)) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/play.svg`.
 */
internal val knotworkPlayIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Play")
    .fillPath("M7 4l13 8-13 8z")
    .build()
