package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.db` glyph (storage / DB) — single-stroke icon family.
 */
internal val knotworkDbIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Db")
    .strokePath("M4 5a8 2.5 0 1 0 16 0a8 2.5 0 1 0 -16 0")
    .strokePath("M4 5v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5V5")
    .strokePath("M4 11v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5v-6")
    .build()
