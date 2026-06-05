package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.link` glyph (link / connect) — single-stroke icon family.
 */
internal val knotworkLinkIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Link")
    .strokePath("M10 14a4 4 0 005.6 0l3-3a4 4 0 00-5.6-5.6l-1 1 M14 10a4 4 0 00-5.6 0l-3 3a4 4 0 005.6 5.6l1-1")
    .build()
