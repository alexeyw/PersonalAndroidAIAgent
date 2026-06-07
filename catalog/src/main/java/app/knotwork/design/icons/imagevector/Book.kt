package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.book` glyph (documentation) — single-stroke icon family.
 */
internal val knotworkBookIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Book")
    .strokePath("M12 6c-1.6-1.3-4.2-2-7-2v13c2.8 0 5.4.7 7 2 1.6-1.3 4.2-2 7-2V4c-2.8 0-5.4.7-7 2z")
    .strokePath("M12 6v13")
    .build()
