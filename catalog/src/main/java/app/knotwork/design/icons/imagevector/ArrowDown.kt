package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.arrowDown` glyph (expand ▽) — single-stroke icon family.
 */
internal val knotworkArrowDownIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ArrowDown")
    .strokePath("M6 9l6 6 6-6")
    .build()
