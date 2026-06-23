package app.knotwork.android.domain.models

/**
 * Framework-free description of a single dynamic launcher shortcut.
 *
 * The presentation layer turns each spec into an `androidx.core.content.pm`
 * `ShortcutInfoCompat` (resolving the icon and the deep-link `Intent`), but the
 * decision of *what* shortcuts to publish — which sessions, in what order, with
 * what labels — lives in [app.knotwork.android.domain.usecases.BuildDynamicShortcutsUseCase]
 * so it stays unit-testable without Android.
 *
 * @property id Stable shortcut id (unique within the dynamic shortcut set). Used
 *   as the `ShortcutInfoCompat` id so re-publishing updates in place.
 * @property sessionId The chat session this shortcut deep-links into.
 * @property shortLabel Launcher short label (long-press menu). Already clamped
 *   to the platform-recommended length.
 * @property longLabel Launcher long label (pinned shortcut). Already clamped to
 *   the platform-recommended length.
 * @property rank Display rank; lower sorts first. Mirrors the recency order so
 *   the launcher shows the most recent sessions first.
 */
data class DynamicShortcutSpec(
    val id: String,
    val sessionId: String,
    val shortLabel: String,
    val longLabel: String,
    val rank: Int,
)
