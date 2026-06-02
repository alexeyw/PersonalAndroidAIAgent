package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.hourglass` glyph (pending / queued) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/hourglass.svg`.
 */
internal val knotworkHourglassIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Hourglass")
    .strokePath("M6 3h12")
    .strokePath("M6 21h12")
    .strokePath("M7 3c0 5 5 6 5 9s-5 4-5 9")
    .strokePath("M17 3c0 5-5 6-5 9s5 4 5 9")
    .build()
