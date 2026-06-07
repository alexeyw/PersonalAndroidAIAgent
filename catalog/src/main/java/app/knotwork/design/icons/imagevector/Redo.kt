package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.redo` glyph (redo) — single-stroke icon family.
 */
internal val knotworkRedoIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Redo")
    .strokePath("M15 14l5-5-5-5")
    .strokePath("M20 9H9a5 5 0 000 10h4")
    .build()
