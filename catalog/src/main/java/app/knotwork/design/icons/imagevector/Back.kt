package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.back` glyph (back) — single-stroke icon family.
 */
internal val knotworkBackIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Back")
    .strokePath("M15 6l-6 6 6 6")
    .build()
