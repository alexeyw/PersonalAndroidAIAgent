package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.minusCircle` glyph (remove item) — single-stroke icon family.
 */
internal val knotworkMinusCircleIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("MinusCircle")
    .strokePath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    .strokePath("M8 12h8")
    .build()
