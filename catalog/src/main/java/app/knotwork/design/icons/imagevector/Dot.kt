package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.dot` glyph (marker dot (solid)) — single-stroke icon family.
 */
internal val knotworkDotIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Dot")
    .fillPath("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
    .build()
