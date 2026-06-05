package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.check` glyph (confirm / tick) — single-stroke icon family.
 */
internal val knotworkCheckIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Check")
    .strokePath("M5 12l4 4 10-10")
    .build()
