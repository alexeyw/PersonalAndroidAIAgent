package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.copy` glyph (copy) — single-stroke icon family.
 */
internal val knotworkCopyIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Copy")
    .strokePath("M8 8h11v12H8z M5 4h11v3 M5 4v12h3")
    .build()
