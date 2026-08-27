package app.knotwork.design.screens.more

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One navigation row on the More tab. The row is fully data-driven so the
 * app-side mapper can swap counters, badges, and visibility without
 * touching the renderer.
 *
 * @property id stable identifier — also used as the [androidx.compose.runtime.key].
 * @property title primary label.
 * @property subtitle optional secondary line (mono); usually a live count.
 * @property icon leading icon glyph.
 * @property badge optional trailing badge count; rendered when > 0.
 * @property onClick navigation callback.
 */
data class MoreRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val badge: Int = 0,
    val onClick: () -> Unit,
)

/**
 * One named group of [MoreRow]s.
 *
 * Sections are **labels, not screens**: nothing is navigated to differently
 * because of them, no row is re-pointed, and the row component is unchanged.
 * They exist because twelve flat rows made the one row a closed-test user was
 * looking for take two hours to find.
 *
 * @property id stable key for the section.
 * @property title section heading, e.g. `"Automation"`.
 * @property rows the rows in this section, in display order.
 */
data class MoreSection(val id: String, val title: String, val rows: List<MoreRow>)

/**
 * Top-level immutable input to `MoreContent`.
 *
 * @property sections ordered, named groups of navigation rows. Order is "why
 * you opened More, most often first"; App sits last because that is where
 * Android users reach for Settings.
 * @property networkStatus footer text (e.g. `"on-device · no network calls in last 14 m"`).
 * Hidden when null/blank.
 * @property networkStatusOk drives the green-dot indicator on the footer
 * pill — `true` when the app has been offline-only for the indicator
 * window, `false` when a recent outbound call has been recorded.
 */
data class MoreViewState(
    val sections: List<MoreSection> = emptyList(),
    val networkStatus: String? = null,
    val networkStatusOk: Boolean = true,
)

/** Bundle of localised display strings threaded into `MoreContent`. */
data class MoreStrings(val title: String = "More", val subtitle: String = "Everything else")
