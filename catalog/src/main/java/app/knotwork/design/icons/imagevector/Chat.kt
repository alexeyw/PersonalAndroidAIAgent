package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.chat` glyph (chat tab) — single-stroke icon family.
 */
internal val knotworkChatIcon: ImageVector by lazy { build(IconStroke.DEFAULT) }

/** Active (2.0 stroke) variant — selected bottom-nav tab / active segmented control. */
internal val knotworkChatActiveIcon: ImageVector by lazy { build(IconStroke.ACTIVE) }

private const val CHAT_PATH = "M4 5h16v11H8l-4 4V5z"

private fun build(strokeWidth: Float): ImageVector =
    iconBuilder("Chat").strokePath(CHAT_PATH, strokeWidth = strokeWidth).build()
