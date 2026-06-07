package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.chip` glyph (on-device / NPU) — single-stroke icon family.
 */
internal val knotworkChipIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Chip")
    .strokePath("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z")
    .strokePath("M9 9h6v6H9z")
    .strokePath("M3 10h3")
    .strokePath("M3 14h3")
    .strokePath("M18 10h3")
    .strokePath("M18 14h3")
    .strokePath("M10 3v3")
    .strokePath("M14 3v3")
    .strokePath("M10 18v3")
    .strokePath("M14 18v3")
    .build()
