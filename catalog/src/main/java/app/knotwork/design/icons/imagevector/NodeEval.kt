package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `EVALUATION` node glyph — balance scale. Source:
 * `project_docs/design/icons-src/node-eval.svg`.
 */
internal val knotworkNodeEvalIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeEval")
    .strokePath("M 12 4 v 16")
    .strokePath("M 5 8 h 14")
    .strokePath("M 12 4 l -2 2 2 2 2 -2 z")
    .strokePath("M 2.5 14 l 2.5 -6 2.5 6 a 2.5 2.5 0 1 1 -5 0 z")
    .strokePath("M 16.5 14 l 2.5 -6 2.5 6 a 2.5 2.5 0 1 1 -5 0 z")
    .strokePath("M 8 20 h 8")
    .build()
