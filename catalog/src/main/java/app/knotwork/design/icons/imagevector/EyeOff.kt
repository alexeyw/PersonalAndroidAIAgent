package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.eyeOff` glyph (hide password) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/eyeOff.svg`.
 */
internal val knotworkEyeOffIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("EyeOff")
    .strokePath("M2 12c2.5-4.7 6-7 10-7s7.5 2.3 10 7c-2.5 4.7-6 7-10 7s-7.5-2.3-10-7z")
    .strokePath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    .strokePath("M4 3l16 18")
    .build()
