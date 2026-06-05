package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.undo` glyph (undo) — single-stroke icon family.
 */
internal val knotworkUndoIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Undo")
    .strokePath("M9 14L4 9l5-5")
    .strokePath("M4 9h11a5 5 0 010 10h-4")
    .build()
