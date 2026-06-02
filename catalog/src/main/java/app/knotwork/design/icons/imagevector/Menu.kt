package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.menu` glyph (hamburger / drawer) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/menu.svg`.
 */
internal val knotworkMenuIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Menu")
    .strokePath("M3 6h18M3 12h18M3 18h18")
    .build()
