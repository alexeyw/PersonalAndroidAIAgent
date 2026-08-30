package app.knotwork.android.buildtools

/**
 * Pure checker that holds every place the released version number is written to
 * one value.
 *
 * The build has exactly one source of truth — `versionName` in
 * `app/build.gradle.kts`, which is what the F-Droid recipe and the release
 * workflow both derive from. The problem is that the number is *repeated* for
 * humans in places no build step reads:
 *
 *  - the version badge in `README.md`, which is the first thing a visitor sees;
 *  - the topmost released heading of `CHANGELOG.md`;
 *  - the `[Unreleased]` compare link at the foot of `CHANGELOG.md`, which says
 *    which tag the unreleased range starts from;
 *  - the link definition of that topmost release, which names the tag it shipped as.
 *
 * Every one of those is edited by hand at release time, and nothing noticed when
 * one was missed. The release checklist did not even mention the README badge.
 * A stale badge is not cosmetic: it is the version number a bug reporter quotes.
 *
 * The checker is a pure `(String, String, String) -> List<String>` transform,
 * so the Gradle task supplies the declared `versionName` and the two documents
 * and this logic stays unit-testable. `versionCode` is out of scope on purpose:
 * its agreement with the store changelog file is already held by
 * `StoreMetadataTest`, and a second, separately-written opinion about the same
 * fact is how two guards start disagreeing.
 */
object VersionSourcesChecker {

    /** The version badge as `README.md` writes it, via shields.io. */
    private val README_BADGE = Regex("""img\.shields\.io/badge/version-([0-9][0-9A-Za-z.\-]*?)-""")

    /** A released changelog heading; `[Unreleased]` deliberately does not match. */
    private val RELEASE_HEADING = Regex("""^##\s+\[(\d+\.\d+\.\d+[0-9A-Za-z.\-]*)]""", RegexOption.MULTILINE)

    /** The `[Unreleased]` compare link, whose base tag is the last release. */
    private val UNRELEASED_COMPARE = Regex("""^\[Unreleased]:\s*\S*/compare/v(\S+?)\.\.\.HEAD\s*$""", RegexOption.MULTILINE)

    /**
     * Verifies that every hand-written copy of the version number agrees with
     * the one the build declares.
     *
     * @param versionName The `versionName` from the app's `defaultConfig`.
     * @param readme The full text of `README.md`.
     * @param changelog The full text of `CHANGELOG.md`.
     * @return One message per disagreement, empty when every source agrees.
     *   Each message names both values and the file to edit.
     */
    fun check(versionName: String, readme: String, changelog: String): List<String> {
        val declared = versionName.trim()
        if (declared.isEmpty()) {
            return listOf("The build declares no `versionName`; set it in `app/build.gradle.kts`.")
        }
        val violations = mutableListOf<String>()
        compare(violations, declared, README_BADGE.find(readme)?.groupValues?.get(1), BADGE_SOURCE)
        val heading = RELEASE_HEADING.find(changelog)?.groupValues?.get(1)
        compare(violations, declared, heading, HEADING_SOURCE)
        compare(violations, declared, UNRELEASED_COMPARE.find(changelog)?.groupValues?.get(1), COMPARE_SOURCE)
        if (heading != null) {
            val definition = Regex("""^\[${Regex.escape(heading)}]:\s*(\S+)\s*$""", RegexOption.MULTILINE)
                .find(changelog)?.groupValues?.get(1)
            when {
                definition == null ->
                    violations += "`CHANGELOG.md` heading `[$heading]` has no link definition at the foot of the file."
                !definition.endsWith("v$heading") ->
                    violations += "`CHANGELOG.md` link definition for `[$heading]` ends at `$definition`, " +
                        "which does not name tag `v$heading`."
            }
        }
        return violations
    }

    /**
     * Records a disagreement between one hand-written source and the build.
     *
     * @param violations The list being accumulated.
     * @param declared The version the build declares.
     * @param found The version read from the source, or `null` when the source
     *   could not be located at all — which is itself a failure, since a
     *   checker that silently finds nothing to compare passes everything.
     * @param source Where the value lives and how to fix it.
     */
    private fun compare(violations: MutableList<String>, declared: String, found: String?, source: Source) {
        when {
            found == null -> violations += "${source.description} could not be found. ${source.hint}"
            found != declared -> violations +=
                "${source.description} says `$found`, but the build declares `$declared`. ${source.hint}"
        }
    }

    /**
     * One hand-written copy of the version number.
     *
     * @property description What and where it is, for the failure message.
     * @property hint What to do about a mismatch.
     */
    private data class Source(val description: String, val hint: String)

    /** The shields.io version badge near the top of the README. */
    private val BADGE_SOURCE = Source(
        "The version badge in `README.md`",
        "Update the `img.shields.io/badge/version-…` badge; see the release checklist in `docs/release.md`.",
    )

    /** The topmost released section heading of the changelog. */
    private val HEADING_SOURCE = Source(
        "The topmost released heading in `CHANGELOG.md`",
        "Move `[Unreleased]` under a heading for the shipping version, or bump `versionName`.",
    )

    /** The compare link that says which tag the unreleased range starts from. */
    private val COMPARE_SOURCE = Source(
        "The `[Unreleased]` compare link at the foot of `CHANGELOG.md`",
        "Point it at `compare/v<version>...HEAD` for the shipping version.",
    )
}
