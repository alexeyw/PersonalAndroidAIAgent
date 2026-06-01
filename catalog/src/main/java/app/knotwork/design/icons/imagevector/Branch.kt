package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.branch` glyph (branch / IF) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/branch.svg`.
 */
internal val knotworkBranchIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Branch")
    .strokePath("M4 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M4 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M16 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M6 8v8")
    .strokePath("M6 12a6 6 0 006 6h0a6 6 0 016-6")
    .build()
