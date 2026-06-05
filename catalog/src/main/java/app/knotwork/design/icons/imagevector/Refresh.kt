package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.refresh` glyph (retry / refresh) — single-stroke icon family.
 */
internal val knotworkRefreshIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Refresh")
    .strokePath("M21 3v6h-6")
    .strokePath("M3 11a9 9 0 0115-6.7L21 9")
    .strokePath("M3 21v-6h6")
    .strokePath("M21 13a9 9 0 01-15 6.7L3 15")
    .build()
