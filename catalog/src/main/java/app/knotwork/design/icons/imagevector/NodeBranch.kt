package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `IF_CONDITION` node glyph — diamond with a check mark.
 */
internal val knotworkNodeBranchIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeBranch")
    .strokePath("M 12 4 l 8 8 -8 8 -8 -8 z")
    .strokePath("M 9 11 l 2 2 4 -4")
    .build()
