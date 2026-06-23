package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.DynamicShortcutSpec
import javax.inject.Inject

/**
 * Builds the dynamic launcher-shortcut set from the user's recent chat sessions.
 *
 * Launchers surface only a handful of shortcuts (commonly two dynamic ones
 * alongside the static ones), so this picks the most recently updated sessions,
 * caps the count, and clamps each label to the platform-recommended lengths
 * (short label ≤ 10 chars, long label ≤ 25). Sessions whose name is blank are
 * skipped — a shortcut with no readable label is noise.
 *
 * Pure and clock-free: the caller supplies the sessions already loaded from the
 * repository; ordering is by [ChatSession.updatedAt] descending so "recent"
 * needs no wall clock here.
 */
class BuildDynamicShortcutsUseCase @Inject constructor() {

    /**
     * Selects and shapes the dynamic shortcuts to publish.
     *
     * @param sessions All known chat sessions (any order).
     * @param maxCount Maximum number of shortcuts to emit. Values below 1 yield
     *   an empty list.
     * @return The shortcut specs in recency order (most recent first), ready for
     *   the presentation layer to convert into `ShortcutInfoCompat`s.
     */
    operator fun invoke(sessions: List<ChatSession>, maxCount: Int = DEFAULT_MAX_SHORTCUTS): List<DynamicShortcutSpec> {
        if (maxCount < 1) return emptyList()
        return sessions
            .asSequence()
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.updatedAt }
            .take(maxCount)
            .mapIndexed { index, session ->
                DynamicShortcutSpec(
                    id = SHORTCUT_ID_PREFIX + session.id,
                    sessionId = session.id,
                    shortLabel = clamp(session.name.trim(), SHORT_LABEL_MAX),
                    longLabel = clamp(session.name.trim(), LONG_LABEL_MAX),
                    rank = index,
                )
            }
            .toList()
    }

    /** Clamps [label] to [max] characters, appending an ellipsis when truncated. */
    private fun clamp(label: String, max: Int): String =
        if (label.length <= max) label else label.take(max - 1).trimEnd() + "…"

    private companion object {
        /** Id prefix marking a shortcut as a per-session deep link. */
        const val SHORTCUT_ID_PREFIX = "session_"

        /** Default dynamic-shortcut budget; launchers typically show two. */
        const val DEFAULT_MAX_SHORTCUTS = 3

        /** Platform-recommended max for a shortcut short label. */
        const val SHORT_LABEL_MAX = 10

        /** Platform-recommended max for a shortcut long label. */
        const val LONG_LABEL_MAX = 25
    }
}
