package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.unarchive` glyph (restore out of the archive) — single-stroke icon family.
 *
 * The `archive` box with its slot swapped for an arrow leaving upward.
 * Deliberately **not** a reuse of `undo`: the archive-restore action and the
 * undo-snackbar action appear at the same moment, one tap apart, and one glyph
 * must not mean both "reverse my last action" and "take out of the archive".
 */
internal val knotworkUnarchiveIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Unarchive")
    .strokePath("M4 4h16a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z")
    .strokePath("M5 8v11a1 1 0 001 1h12a1 1 0 001-1V8")
    .strokePath("M12 17v-5.4")
    .strokePath("M9.4 14.2l2.6-2.6 2.6 2.6")
    .build()
