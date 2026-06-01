package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.bookmark` glyph (saved / bookmark) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/bookmark.svg`.
 */
internal val knotworkBookmarkIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Bookmark")
    .strokePath("M6 4h12v16l-6-4-6 4z")
    .build()
