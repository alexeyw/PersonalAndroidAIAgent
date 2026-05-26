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
 * Top-level immutable input to `MoreContent`.
 *
 * @property rows ordered list of navigation rows.
 * @property networkStatus footer text (e.g. `"on-device · no network calls in last 14 m"`).
 * Hidden when null/blank.
 * @property networkStatusOk drives the green-dot indicator on the footer
 * pill — `true` when the app has been offline-only for the indicator
 * window, `false` when a recent outbound call has been recorded.
 */
data class MoreViewState(
    val rows: List<MoreRow> = emptyList(),
    val networkStatus: String? = null,
    val networkStatusOk: Boolean = true,
)

/** Bundle of localised display strings threaded into `MoreContent`. */
data class MoreStrings(
    val title: String = "More",
    val subtitle: String = "Everything else",
    val searchCd: String = "Search",
)
