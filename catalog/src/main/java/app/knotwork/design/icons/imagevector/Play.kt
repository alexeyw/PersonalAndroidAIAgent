package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.play` glyph (run (solid)) — single-stroke icon family.
 */
internal val knotworkPlayIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Play")
    .fillPath("M7 4l13 8-13 8z")
    .build()
