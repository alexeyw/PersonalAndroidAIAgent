package app.knotwork.android.buildtools

/**
 * Guards the Play store listing against Google's hard character limits.
 *
 * These limits are not style: the Play Console **rejects** an upload that
 * exceeds them. Without this gate the rejection lands after the merge, after
 * the release workflow has built and signed an artefact, in a web form nobody
 * looks at until they are trying to ship — which is exactly the wrong moment to
 * discover that one added clause put the description four characters over.
 *
 * The margins are the reason this exists rather than a note in a checklist.
 * When the gate was written the English description sat 5 characters under its
 * ceiling, the English title 4 under, and one changelog **2**. A single word is
 * enough to break any of them, and nothing in the repository could see it.
 *
 * Counting is in **Unicode code points**, matching how the Console counts, and
 * a single trailing newline is ignored because every file in the tree ends with
 * one and Play does not count it.
 *
 * A pure `Map<path, content> -> List<Violation>` transform: no file-system or
 * Git access, so it is trivially unit-testable. The Gradle task resolves the
 * `fastlane/metadata` tree and feeds it here.
 */
object StoreListingLengthChecker {

    /**
     * One listing field and the ceiling Play enforces on it.
     *
     * @property fileName Base name of the file holding the field.
     * @property limit Maximum number of code points Play accepts.
     * @property label Human-readable field name for the failure message.
     */
    enum class Field(val fileName: String, val limit: Int, val label: String) {
        /** App name shown on the store page and in search results. */
        TITLE("title.txt", 30, "app title"),

        /** One-line summary under the app name. */
        SHORT_DESCRIPTION("short_description.txt", 80, "short description"),

        /** The full store description. */
        FULL_DESCRIPTION("full_description.txt", 4_000, "full description"),
    }

    /**
     * Ceiling on one release's "What's new" text.
     *
     * Held apart from [Field] because changelog files are named after the
     * version code rather than after the field, so they are matched by their
     * parent directory instead of their file name.
     */
    const val CHANGELOG_LIMIT: Int = 500

    /** Directory name holding the per-version changelog files. */
    private const val CHANGELOG_DIR: String = "changelogs"

    /**
     * A field over its limit.
     *
     * @property file Path of the offending file, relative to the repository root.
     * @property label Human-readable field name.
     * @property length Actual length in code points.
     * @property limit The ceiling it exceeded.
     */
    data class Violation(val file: String, val label: String, val length: Int, val limit: Int) {
        /** One-line rendering for the build failure message. */
        override fun toString(): String = "$file: $label is $length characters, over the $limit-character limit"
    }

    /**
     * Scans store-listing files for over-length fields.
     *
     * @param files Path (repository-relative) to file content, for every file
     *   under the metadata tree. Files that are not a known listing field are
     *   ignored, so screenshots and unrelated assets can share the tree.
     * @return Every field over its limit, in the iteration order of [files].
     */
    fun scan(files: Map<String, String>): List<Violation> = files.mapNotNull { (path, content) ->
        val limit = limitFor(path) ?: return@mapNotNull null
        // One trailing newline is the tree's own convention and not part of the
        // text Play receives; anything beyond that is real content.
        val length = content.removeSuffix("\n").codePointCount()
        if (length > limit.second) Violation(path, limit.first, length, limit.second) else null
    }

    /**
     * The label and ceiling that apply to [path], or `null` when the file is
     * not a listing field this gate knows about.
     *
     * @param path Repository-relative path.
     * @return Label-to-limit pair, or `null`.
     */
    private fun limitFor(path: String): Pair<String, Int>? {
        val segments = path.split('/')
        val name = segments.lastOrNull() ?: return null
        if (segments.getOrNull(segments.size - 2) == CHANGELOG_DIR) {
            return "release notes" to CHANGELOG_LIMIT
        }
        return Field.entries.firstOrNull { it.fileName == name }?.let { it.label to it.limit }
    }

    /**
     * Length in Unicode code points rather than UTF-16 units.
     *
     * `String.length` would over-count anything outside the basic plane — an
     * emoji in a description reads as two — and under a gate whose whole value
     * is a handful of characters of margin, counting the wrong unit defeats it.
     */
    private fun String.codePointCount(): Int = codePointCount(0, length)
}
