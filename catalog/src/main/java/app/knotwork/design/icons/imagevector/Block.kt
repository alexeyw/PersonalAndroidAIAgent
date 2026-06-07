package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.block` glyph (blocked) — single-stroke icon family.
 */
internal val knotworkBlockIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Block")
    .strokePath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    .strokePath("M5.6 5.6l12.8 12.8")
    .build()
