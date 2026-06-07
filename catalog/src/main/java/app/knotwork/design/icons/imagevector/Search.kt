package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.search` glyph (search) — single-stroke icon family.
 */
internal val knotworkSearchIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Search")
    .strokePath("M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0 M20 20l-4-4")
    .build()
