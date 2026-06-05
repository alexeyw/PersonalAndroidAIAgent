package app.knotwork.design.components.chips

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Size variants for [KnotworkFilterChip] / [KnotworkSuggestionChip] / [KnotworkInputChip].
 *
 * Spec mapping:
 *  - [Xs] (28 dp) — dense rows where vertical space is at premium.
 *  - [Sm] (32 dp) — default for segmented controls, library filters,
 *    yes/no toggles.
 *  - [Md] (40 dp) — quick-reply chips under `CLARIFICATION` and HITL cards.
 */
enum class KnotworkChipSize { Xs, Sm, Md }

/**
 * Shared size, padding, icon, and motion defaults for every Knotwork chip
 * atom. Numbers track the design spec for chips.
 * Inline literals at call sites would scatter the chip family — prefer
 * reading from this object.
 */
object KnotworkChipDefaults {

    /** Visual height for [KnotworkChipSize.Xs] — 28 dp. */
    val HeightXs = 28.dp

    /** Visual height for [KnotworkChipSize.Sm] — 32 dp (default). */
    val HeightSm = 32.dp

    /** Visual height for [KnotworkChipSize.Md] — 40 dp (quick-reply). */
    val HeightMd = 40.dp

    /** Horizontal padding for [KnotworkChipSize.Xs] — 10 dp. */
    val PaddingXs = PaddingValues(horizontal = 10.dp)

    /** Horizontal padding for [KnotworkChipSize.Sm] — 12 dp. */
    val PaddingSm = PaddingValues(horizontal = 12.dp)

    /** Horizontal padding for [KnotworkChipSize.Md] — 16 dp. */
    val PaddingMd = PaddingValues(horizontal = 16.dp)

    /** Leading-icon size for [KnotworkChipSize.Xs] — 14 dp. */
    val IconSizeXs = 14.dp

    /** Leading-icon size for [KnotworkChipSize.Sm] — 16 dp. */
    val IconSizeSm = 16.dp

    /** Leading-icon size for [KnotworkChipSize.Md] — 18 dp. */
    val IconSizeMd = 18.dp

    /** Gap between leading icon and label — 6 dp. */
    val IconGap = 6.dp

    /** Cross-fade duration for selected/unselected container colour and border — 180 ms. */
    const val TOGGLE_DURATION_MS = 180

    /** Border width for unselected / outline chip states. */
    val BorderDefault = 1.dp
}
