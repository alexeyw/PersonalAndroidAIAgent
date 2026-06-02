package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.eye` glyph (show password) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/eye.svg`.
 */
internal val knotworkEyeIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Eye")
    .strokePath("M2 12c2.5-4.7 6-7 10-7s7.5 2.3 10 7c-2.5 4.7-6 7-10 7s-7.5-2.3-10-7z")
    .strokePath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    .build()
