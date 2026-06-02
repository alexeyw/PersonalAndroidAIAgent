package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.mic` glyph (voice input) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/mic.svg`.
 */
internal val knotworkMicIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Mic")
    .strokePath("M12 3a3 3 0 00-3 3v5a3 3 0 006 0V6a3 3 0 00-3-3z")
    .strokePath("M18 11a6 6 0 01-12 0")
    .strokePath("M12 17v3")
    .strokePath("M9 21h6")
    .build()
