package app.knotwork.design.a11y

import androidx.compose.ui.unit.dp

/**
 * The minimum interactive size every tappable control must present.
 *
 * **Do not** rely on Material to supply it: the `IconButton` in this Material
 * line lays out at 40 dp and applies no minimum interactive size of its own, so
 * an icon button is 8 dp short unless a call site says otherwise. Several did
 * worse, pinning the button to the size they wanted the *ripple* to be — 36 dp,
 * 32 dp, even 28 dp — which looks right in a screenshot and misses under a
 * thumb.
 *
 * So: size the **glyph** freely, and pin the **button** to this. A control that
 * genuinely cannot afford 48 dp is a design decision, not a rounding choice —
 * document the deviation at the site, as `KnotworkTextField.TrailingHitArea`
 * does.
 */
val MinTouchTarget = 48.dp
