package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.branch` glyph (branch / IF — git-branch) — single-stroke icon family.
 */
internal val knotworkBranchIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Branch")
    .strokePath("M3.9 18a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0 -4.2 0")
    .strokePath("M15.9 6a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0 -4.2 0")
    .strokePath("M6 16V5")
    .strokePath("M18 8.1a9 9 0 01-9 9")
    .build()
