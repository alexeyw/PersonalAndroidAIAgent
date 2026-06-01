package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.shield` glyph (privacy / security) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/shield.svg`.
 */
internal val knotworkShieldIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Shield")
    .strokePath("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    .build()
