package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.bookmarkAdd` glyph (add to bookmarks) — single-stroke icon family.
 */
internal val knotworkBookmarkAddIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("BookmarkAdd")
    .strokePath("M5 5h7v15l-3.5-2.5L5 20z")
    .strokePath("M16 4v6")
    .strokePath("M13 7h6")
    .build()
