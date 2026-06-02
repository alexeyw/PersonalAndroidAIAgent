package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.history` glyph (history) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/history.svg`.
 */
internal val knotworkHistoryIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("History")
    .strokePath("M3 12a9 9 0 109-9v3M3 4v5h5 M12 8v4l3 2")
    .build()
