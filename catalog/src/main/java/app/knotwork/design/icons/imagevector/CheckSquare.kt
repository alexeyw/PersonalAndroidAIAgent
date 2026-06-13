package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.checkSquare` glyph (select all / checkbox tick) — single-stroke icon family.
 */
internal val knotworkCheckSquareIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("CheckSquare")
    .strokePath("M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-14a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1z")
    .strokePath("M8.5 12l2.5 2.5L16 9")
    .build()
