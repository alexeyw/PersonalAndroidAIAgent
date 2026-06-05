package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.archive` glyph (archive) — single-stroke icon family.
 */
internal val knotworkArchiveIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Archive")
    .strokePath("M4 4h16a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z")
    .strokePath("M5 8v11a1 1 0 001 1h12a1 1 0 001-1V8")
    .strokePath("M10 12h4")
    .build()
