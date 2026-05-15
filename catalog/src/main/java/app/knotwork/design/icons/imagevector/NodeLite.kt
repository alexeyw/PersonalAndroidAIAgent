package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `LITE_RT` node glyph — silicon chip with a lightning spark
 * indicating local on-device inference. Source:
 * `project_docs/design/icons-src/node-lite.svg`.
 */
internal val knotworkNodeLiteIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeLite")
    .strokePath(roundedRectPath(x = 5f, y = 5f, w = 14f, h = 14f, rx = 2f))
    .strokePath(
        "M 3 9 h 2 M 3 12 h 2 M 3 15 h 2 " +
            "M 19 9 h 2 M 19 12 h 2 M 19 15 h 2 " +
            "M 9 3 v 2 M 12 3 v 2 M 15 3 v 2 " +
            "M 9 19 v 2 M 12 19 v 2 M 15 19 v 2",
    )
    .fillPath("M 13 8 l -3 5 h 2.5 l -1 4 3 -5 h -2.5 z")
    .build()
