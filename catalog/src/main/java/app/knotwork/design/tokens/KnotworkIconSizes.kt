@file:Suppress("MagicNumber") // Token file — the dp values ARE the icon render-size scale.

package app.knotwork.design.tokens

import androidx.compose.ui.unit.dp

/**
 * Icon render-size tokens. The custom `AppIcons.*` vectors use a
 * fixed 24×24 viewBox; these are the on-screen `Modifier.size` targets per
 * surface context, so a glyph reads at a consistent optical size everywhere it
 * appears.
 *
 * @property AppBar app-bar leading / trailing glyphs and bottom-nav items.
 * @property Nav overflow-menu and dropdown glyphs.
 * @property Inline glyphs inside list rows, chips, and fields.
 * @property Fab extended / regular FAB glyph.
 * @property Micro glyphs inside pills and dense tags.
 */
object KnotworkIconSizes {
    val AppBar = 22.dp
    val Nav = 20.dp
    val Inline = 18.dp
    val Fab = 26.dp
    val Micro = 14.dp
}
