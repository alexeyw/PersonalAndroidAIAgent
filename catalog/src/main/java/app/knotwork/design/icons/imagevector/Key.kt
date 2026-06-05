package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.key` glyph (API key) — single-stroke icon family.
 */
internal val knotworkKeyIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Key")
    .strokePath("M3.5 14a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0 M10 13l9-9M16 7l3 3")
    .build()
