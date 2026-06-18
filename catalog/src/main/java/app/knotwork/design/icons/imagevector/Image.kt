package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.image` glyph (image attachment affordance / Photo-library row / thumbnail) —
 * single-stroke icon family. Frame + sun + mountain ridge.
 */
internal val knotworkImageIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Image")
    .strokePath(
        "M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1 -1.5 1.5" +
            "h-14a1.5 1.5 0 0 1 -1.5 -1.5v-12a1.5 1.5 0 0 1 1.5 -1.5z",
    )
    .strokePath(circlePath(cx = 9f, cy = 10f, r = 1.6f))
    .strokePath("M4 16.5l4.6-3.6 3.1 2.4 3.8-3.9 4.5 4.6")
    .build()
