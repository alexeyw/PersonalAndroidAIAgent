package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.minus` glyph (minus / zoom-out) — single-stroke icon family.
 */
internal val knotworkMinusIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Minus")
    .strokePath("M5 12h14")
    .build()
