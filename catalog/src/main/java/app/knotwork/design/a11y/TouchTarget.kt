package app.knotwork.design.a11y

import androidx.compose.ui.unit.dp

/**
 * The minimum interactive size every tappable control must present.
 *
 * Material's `IconButton` already reserves this via
 * `Modifier.minimumInteractiveComponentSize()` — but an explicit
 * `Modifier.size(…)` on the button **overrides** that, silently shrinking the
 * touch target while the glyph still looks right. Every regression of this kind
 * in the catalog has come from exactly that: a row author pinning the button to
 * the size they wanted the *ripple* to be.
 *
 * So: size the **glyph** freely, and pin the **button** to this. A control that
 * genuinely cannot afford 48 dp is a design decision, not a rounding choice —
 * document the deviation at the site, as `KnotworkTextField.TrailingHitArea`
 * does.
 */
val MinTouchTarget = 48.dp
