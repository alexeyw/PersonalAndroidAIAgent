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
 *  - the link definition of that topmost release, which names the tag it shipped as;
 *  - the pre-release sentence in `README.md` (\"currently at **version X**\");
 *  - every version-like token in `SECURITY.md` — its prose, the line it says
 *    fixes land on, and both cells of the supported-versions table;
 *  - the pre-release **line** named at the top of `docs/roadmap.md`, which says
 *    which release the "where the project is today" list describes.
 *
 * Every one of those is edited by hand at release time, and nothing noticed when
 * one was missed. The release checklist did not even mention the README badge.
 * A stale badge is not cosmetic: it is the version number a bug reporter quotes.
 *
 * **The last two were added after the guard had already shipped**, which is the
 * more useful half of the story: the first version of this checker held four of
 * the seven hand-written copies a survey had counted, and the very next release
 * cut duly left `SECURITY.md` claiming the previous version — in its prose *and*
 * in its supported-versions table, where a stale number tells a reporter their
 * release is unsupported. A rule covering four of seven does not merely miss
 * three; it reads as a closed subject, which is worse than no rule at all.
 *
 * `SECURITY.md` is checked **wholesale** rather than by targeted patterns: every
 * version-like token in it must be the shipping version or its minor line. That
 * is a stronger claim than a regex per sentence and it is true of this document
 * — unlike `README.md`, which legitimately discusses older releases (the
 * one-time signing change at 0.7.0) and so is checked by named patterns only.
 * If a historic version ever needs naming in `SECURITY.md`, this rule is what
 * has to be revisited, deliberately.
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
    private val UNRELEASED_COMPARE = Regex(
        """^\[Unreleased]:\s*\S*/compare/v(\S+?)\.\.\.HEAD\s*$""",
        RegexOption.MULTILINE,
    )

    /** The pre-release sentence in `README.md`, which names the version in prose. */
    private val README_PROSE = Regex("""currently at \*\*version ([0-9][0-9A-Za-z.\-]*)\*\*""")

    /** The pre-release line named at the top of `docs/roadmap.md`. */
    private val ROADMAP_LINE = Regex("""current pre-release line \(`(\d+\.\d+\.x)`\)""")

    /**
     * Any version-like token: `1.2.3`, `1.2.x`, with an optional suffix.
     *
     * Used only on `SECURITY.md`, where every such token is meant to track the
     * shipping release. See the class doc for why that file is checked wholesale
     * and `README.md` is not.
     */
    private val VERSION_TOKEN = Regex("""\b(\d+\.\d+\.(?:\d+|x)[0-9A-Za-z.\-]*)\b""")

    /**
     * Verifies that every hand-written copy of the version number agrees with
     * the one the build declares.
     *
     * @param versionName The `versionName` from the app's `defaultConfig`.
     * @param readme The full text of `README.md`.
     * @param changelog The full text of `CHANGELOG.md`.
     * @param security The full text of `SECURITY.md`.
     * @param roadmap The full text of `docs/roadmap.md`.
     * @return One message per disagreement, empty when every source agrees.
     *   Each message names both values and the file to edit.
     */
    fun check(
        versionName: String,
        readme: String,
        changelog: String,
        security: String,
        roadmap: String,
    ): List<String> {
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
        compare(violations, declared, README_PROSE.find(readme)?.groupValues?.get(1), PROSE_SOURCE)
        violations += checkSecurity(declared, security)
        compare(
            violations,
            minorLineOf(declared) ?: declared,
            ROADMAP_LINE.find(roadmap)?.groupValues?.get(1),
            ROADMAP_SOURCE,
        )
        return violations
    }

    /**
     * Verifies every version-like token in `SECURITY.md`.
     *
     * The document names the version three ways — the pre-release sentence, the
     * line saying which line fixes land on, and the supported-versions table —
     * and all three mean the shipping release. So the rule is stated over tokens
     * rather than over sentences: each must be the full version or its minor
     * line, and a document with no token at all fails, because a checker that
     * finds nothing to compare passes everything.
     *
     * @param declared The version the build declares.
     * @param security The full text of `SECURITY.md`.
     * @return One message per disagreeing token, deduplicated.
     */
    private fun checkSecurity(declared: String, security: String): List<String> {
        val minorLine = minorLineOf(declared)
        val tokens = VERSION_TOKEN.findAll(security).map { it.groupValues[1] }.toList()
        if (tokens.isEmpty()) {
            return listOf(
                "`SECURITY.md` names no version at all. It is meant to state the supported release line; " +
                    "if that sentence was removed, this rule has to be revisited deliberately.",
            )
        }
        return tokens.filter { it != declared && it != minorLine }.distinct().map { stale ->
            "`SECURITY.md` says `$stale`, but the build declares `$declared`. " +
                "Every version in that file tracks the shipping release — the pre-release sentence, the " +
                "supported line, and both cells of the supported-versions table (`$minorLine` and `< $declared`)."
        }
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
     * The minor line a version belongs to: `0.9.0` -> `0.9.x`.
     *
     * Two documents talk about the *line* rather than the release — the
     * supported-versions table and the roadmap's opening sentence — so the
     * comparison for those is against this, not against the full version.
     *
     * @param declared The version the build declares.
     * @return The minor line, or `null` for a version with no minor component.
     */
    private fun minorLineOf(declared: String): String? =
        declared.split(".").take(2).takeIf { it.size == 2 }?.joinToString(".", postfix = ".x")

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

    /** The pre-release sentence in the README, which names the version in prose. */
    private val PROSE_SOURCE = Source(
        "The pre-release sentence in `README.md`",
        "Update \"currently at **version …**\" in the *Pre-release notice*; see `docs/release.md`.",
    )

    /** The pre-release line the roadmap says it is describing. */
    private val ROADMAP_SOURCE = Source(
        "The pre-release line in `docs/roadmap.md`",
        "Update \"The current pre-release line (`X.Y.x`)\" to the shipping line.",
    )

    /** The compare link that says which tag the unreleased range starts from. */
    private val COMPARE_SOURCE = Source(
        "The `[Unreleased]` compare link at the foot of `CHANGELOG.md`",
        "Point it at `compare/v<version>...HEAD` for the shipping version.",
    )
}
