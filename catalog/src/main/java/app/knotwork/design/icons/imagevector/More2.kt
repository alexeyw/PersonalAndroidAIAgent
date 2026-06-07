package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.more2` glyph (overflow … (horizontal)) — single-stroke icon family.
 */
internal val knotworkMore2Icon: ImageVector by lazy { build(IconStroke.DEFAULT) }

/** Active (2.0 stroke) variant — selected bottom-nav tab / active segmented control. */
internal val knotworkMore2ActiveIcon: ImageVector by lazy { build(IconStroke.ACTIVE) }

private const val MORE2_PATH = "M5 12h14M5 6h14M5 18h14"

private fun build(strokeWidth: Float): ImageVector =
    iconBuilder("More2").strokePath(MORE2_PATH, strokeWidth = strokeWidth).build()
