package app.knotwork.android.domain.settings

import app.knotwork.android.domain.settings.SettingControlType.LINK

/**
 * Which field of a [SettingsSearchableEntry] a query matched. Drives both result
 * ranking (lower ordinal ranks higher) and the presentation hints — a [NAME]
 * match carries a highlight range, a [SYNONYM] match surfaces the `≈ "…"` chip.
 */
enum class SettingsSearchField {
    /** The query was found in the human-readable setting name. */
    NAME,

    /** The query matched one of the registry synonyms (and not the name). */
    SYNONYM,

    /** The query matched the owning category title. */
    CATEGORY,

    /** The query matched the setting description. */
    DESCRIPTION,
}

/**
 * One indexable settings row with its presentation strings already resolved.
 *
 * The pure-domain [SettingsRegistry] only models structure; the presentation
 * layer resolves the localized [name], [description] and [categoryTitle] from
 * string resources and hands a list of these entries to [SettingsSearchEngine].
 * Keeping the resolved text here (rather than `@StringRes` ids) lets the search
 * algorithm stay Android-free and fully unit-testable.
 *
 * @property anchorKey Stable identifier of the row, used to deep-link and
 *   highlight it on its category sub-screen (see [SettingEntry.anchorKey]).
 * @property categoryId The category that owns the row.
 * @property tier Visibility tier of the row within its category.
 * @property name Human-readable setting name (the primary match field).
 * @property description One-line description of what the setting does; blank when
 *   the row has none.
 * @property categoryTitle Human-readable title of the owning category.
 * @property synonyms Extra English search terms carried over from the registry.
 */
data class SettingsSearchableEntry(
    val anchorKey: String,
    val categoryId: SettingsCategoryId,
    val tier: SettingTier,
    val name: String,
    val description: String,
    val categoryTitle: String,
    val synonyms: List<String>,
)

/**
 * A single settings-search hit.
 *
 * @property entry The matched indexable entry.
 * @property matchedField The highest-priority field the query matched.
 * @property nameMatchRange Range of the query inside [SettingsSearchableEntry.name]
 *   when the name matched (for substring highlighting); `null` otherwise.
 * @property synonymHit The synonym that caused the hit when the name itself did
 *   not match (surfaces the `≈ "…"` chip); `null` otherwise.
 */
data class SettingsSearchResult(
    val entry: SettingsSearchableEntry,
    val matchedField: SettingsSearchField,
    val nameMatchRange: IntRange?,
    val synonymHit: String?,
)

/**
 * Pure-domain filtering and ranking over the settings index.
 *
 * Operates on already-resolved [SettingsSearchableEntry] values so the algorithm
 * carries no Android dependency and is exhaustively unit-testable. Matching is
 * case-insensitive substring matching across name, synonyms, category title and
 * description; results are ranked by match quality and, within a rank, by the
 * input order (which the presentation layer keeps in registry display order).
 */
object SettingsSearchEngine {

    /**
     * Filters [entries] by [query] and returns the ranked matches.
     *
     * A blank query yields an empty list (the hub then renders its normal body
     * rather than a result list). Ranking, best first:
     * 1. name prefix match,
     * 2. name substring match,
     * 3. synonym match,
     * 4. category-title match,
     * 5. description match.
     *
     * The sort is stable, so equally-ranked rows preserve their input order.
     *
     * @param query Raw user query (trimmed and lower-cased internally).
     * @param entries The full settings index, in display order.
     * @return Matching results, best match first; empty when nothing matches or
     *   the query is blank.
     */
    fun search(query: String, entries: List<SettingsSearchableEntry>): List<SettingsSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return entries
            .mapNotNull { match(needle, it) }
            .sortedBy { it.rank() }
    }

    /**
     * Computes the best match of [needle] against a single [entry], or `null`
     * when the entry does not match at all.
     */
    private fun match(needle: String, entry: SettingsSearchableEntry): SettingsSearchResult? {
        val nameIndex = entry.name.lowercase().indexOf(needle)
        if (nameIndex >= 0) {
            return SettingsSearchResult(
                entry = entry,
                matchedField = SettingsSearchField.NAME,
                nameMatchRange = nameIndex until (nameIndex + needle.length),
                synonymHit = null,
            )
        }
        val synonymHit = entry.synonyms.firstOrNull { it.lowercase().contains(needle) }
        if (synonymHit != null) {
            return SettingsSearchResult(
                entry = entry,
                matchedField = SettingsSearchField.SYNONYM,
                nameMatchRange = null,
                synonymHit = synonymHit,
            )
        }
        if (entry.categoryTitle.lowercase().contains(needle)) {
            return SettingsSearchResult(entry, SettingsSearchField.CATEGORY, nameMatchRange = null, synonymHit = null)
        }
        if (entry.description.lowercase().contains(needle)) {
            return SettingsSearchResult(
                entry,
                SettingsSearchField.DESCRIPTION,
                nameMatchRange = null,
                synonymHit = null,
            )
        }
        return null
    }

    /**
     * Sort key: lower ranks first. A name match that starts at index 0 (prefix)
     * outranks a mid-string name match; the remaining fields follow the
     * [SettingsSearchField] ordinal order.
     */
    private fun SettingsSearchResult.rank(): Int = when (matchedField) {
        SettingsSearchField.NAME -> if (nameMatchRange?.first == 0) RANK_NAME_PREFIX else RANK_NAME_SUBSTRING
        SettingsSearchField.SYNONYM -> RANK_SYNONYM
        SettingsSearchField.CATEGORY -> RANK_CATEGORY
        SettingsSearchField.DESCRIPTION -> RANK_DESCRIPTION
    }

    private const val RANK_NAME_PREFIX = 0
    private const val RANK_NAME_SUBSTRING = 1
    private const val RANK_SYNONYM = 2
    private const val RANK_CATEGORY = 3
    private const val RANK_DESCRIPTION = 4
}

/**
 * Stable, globally-unique anchor identifier for a settings row, used to deep-link
 * a search result to its category sub-screen and highlight the destination row.
 *
 * For a persisted setting the anchor is its [SettingEntry.key]. Keyless rows
 * (links, the identity card, the reset action, the memory-action cluster) derive
 * a stable anchor from their [SettingEntry.controlType] and, for links, the
 * [SettingEntry.linkDestination] — each such row is unique within the registry,
 * so the derived anchors never collide.
 *
 * @return The row's stable anchor key.
 */
fun SettingEntry.anchorKey(): String = key ?: when (controlType) {
    LINK -> "LINK_" + (linkDestination ?: controlType.name).uppercase().replace(NON_ALNUM, "_").trim('_')
    else -> controlType.name
}

/** Matches any run of characters that are not ASCII letters or digits. */
private val NON_ALNUM = Regex("[^A-Z0-9]+")
