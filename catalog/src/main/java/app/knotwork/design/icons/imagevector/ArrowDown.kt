package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowDown` glyph (expand ▽) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/arrowDown.svg`.
 */
internal val knotworkArrowDownIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowDown")
    .strokePath("M6 9l6 6 6-6")
    .build()
