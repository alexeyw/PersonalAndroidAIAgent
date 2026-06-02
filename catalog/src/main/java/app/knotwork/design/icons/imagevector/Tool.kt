package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.tool` glyph (tool) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/tool.svg`.
 */
internal val knotworkToolIcon: ImageVector by lazy { build(IconStroke.DEFAULT) }

/** Active (2.0 stroke) variant — selected bottom-nav tab / active segmented control. */
internal val knotworkToolActiveIcon: ImageVector by lazy { build(IconStroke.ACTIVE) }

private const val TOOL_PATH = "M14 4l6 6-9 9H5v-6z M11 7l6 6"

private fun build(strokeWidth: Float): ImageVector =
    iconBuilder("Tool").strokePath(TOOL_PATH, strokeWidth = strokeWidth).build()
