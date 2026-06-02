package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.bolt` glyph (quick action / energy) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/bolt.svg`.
 */
internal val knotworkBoltIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Bolt")
    .strokePath("M13 3L5 14h6l-1 7 8-11h-6z")
    .build()
