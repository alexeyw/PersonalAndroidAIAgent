package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.check` glyph (confirm / tick) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/check.svg`.
 */
internal val knotworkCheckIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Check")
    .strokePath("M5 12l4 4 10-10")
    .build()
