package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.extension` glyph (plugin / extension) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/extension.svg`.
 */
internal val knotworkExtensionIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Extension")
    .strokePath(
        "M8 5a2 2 0 114 0h3a1 1 0 011 1v3a2 2 0 110 4v3a1 1 0 01-1 1h-3" +
            "a2 2 0 10-4 0H5a1 1 0 01-1-1v-3a2 2 0 110-4V6a1 1 0 011-1h3z",
    )
    .build()
