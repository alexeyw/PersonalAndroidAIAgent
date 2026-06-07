package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.filter` glyph (filter / facets) — single-stroke icon family.
 */
internal val knotworkFilterIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Filter")
    .strokePath("M4 5h16l-6 8v6l-4-2v-4z")
    .build()
