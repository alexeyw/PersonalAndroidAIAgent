package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.undo` glyph (undo) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/undo.svg`.
 */
internal val knotworkUndoIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Undo")
    .strokePath("M9 14L4 9l5-5")
    .strokePath("M4 9h11a5 5 0 010 10h-4")
    .build()
