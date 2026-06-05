package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.circle` glyph (empty state dot (radio unchecked)) — single-stroke icon family.
 */
internal val knotworkCircleIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Circle")
    .strokePath("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0")
    .build()
