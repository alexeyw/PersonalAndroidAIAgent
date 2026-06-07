package app.knotwork.design.components.controls

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Size variants for [KnotworkTextField] / [KnotworkTextArea] / [KnotworkPasswordField].
 *
 * Spec mapping:
 *  - [Sm] (40 dp) — primary form fields (NodeConfigSheet, settings rows).
 *  - [Md] (48 dp) — search bars (`MoreScreen`, `PromptLibraryScreen`).
 *  - [Lg] (56 dp) — hero fields in onboarding (token, model URL).
 *  - [Composer] (48 dp · pill) — only the chat composer.
 */
enum class KnotworkFieldSize { Sm, Md, Lg, Composer }

/**
 * Shared size, padding, border, and gap defaults for every Knotwork text-input
 * atom. Centralising the numbers here keeps the field family pixel-consistent
 * across the catalog and gives downstream call sites a single token to override
 * (e.g. `Modifier.heightIn(min = KnotworkFieldDefaults.HeightSm)`).
 *
 * Numbers track the design spec for fields. Do not
 * inline literals at call sites — read from this object so future tuning lands
 * in one place.
 */
object KnotworkFieldDefaults {

    /** Visual height for [KnotworkFieldSize.Sm] — 40 dp (primary form fields). */
    val HeightSm = 40.dp

    /** Visual height for [KnotworkFieldSize.Md] — 48 dp (search bars). */
    val HeightMd = 48.dp

    /** Visual height for [KnotworkFieldSize.Lg] — 56 dp (onboarding hero fields). */
    val HeightLg = 56.dp

    /** Visual height for [KnotworkFieldSize.Composer] — 48 dp pill (chat composer). */
    val HeightComposer = 48.dp

    /** Single-line container padding for [KnotworkFieldSize.Sm]: 12 dp · 8 dp. */
    val PaddingSm = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    /** Single-line container padding for [KnotworkFieldSize.Md]: 14 dp · 10 dp. */
    val PaddingMd = PaddingValues(horizontal = 14.dp, vertical = 10.dp)

    /** Single-line container padding for [KnotworkFieldSize.Lg]: 16 dp · 14 dp. */
    val PaddingLg = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

    /** Multi-line container padding for [KnotworkTextArea]: 12 dp horizontal · 12 dp vertical. */
    val PaddingTextArea = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

    /** Default 1 dp border for the resting, hovered, readOnly, disabled, and filled states. */
    val BorderDefault = 1.dp

    /** 2 dp border for the focused state (replaces [BorderDefault] without growing the box). */
    val BorderFocused = 2.dp

    /** 2 dp border for the error state (replaces [BorderDefault], coloured `riskDestructive`). */
    val BorderError = 2.dp

    /** Leading-icon size — 18 dp; inset matches container horizontal padding. */
    val LeadingIconSize = 18.dp

    /** Trailing-icon size — 20 dp; clickable hit-area is at least 40 dp. */
    val TrailingIconSize = 20.dp

    /** Gap between leading/trailing icon and the text — `sp2` (8 dp). */
    val IconGap = 8.dp

    /** Vertical gap from external label to the input box — `sp2` (8 dp). */
    val LabelGap = 8.dp

    /** Vertical gap from the input box to the helper / counter row — `sp1` (4 dp). */
    val HelperGap = 4.dp

    /** Vertical gap between two adjacent [KnotworkField] siblings in a form — `sp3` (12 dp). */
    val FieldGap = 12.dp
}
