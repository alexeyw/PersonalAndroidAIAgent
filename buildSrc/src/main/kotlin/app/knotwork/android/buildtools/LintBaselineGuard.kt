package app.knotwork.android.buildtools

/**
 * Pure scanner that keeps the dependency-drift report from being silently
 * deleted by a lint baseline.
 *
 * A handful of lint checks answer from an external version index or from the
 * calendar rather than from the contents of the repository, so their verdict can
 * change with nothing committed in between. They are therefore demoted to
 * informational severity in every strict module: they still run and still land
 * in the lint report, but they no longer fail the build. The report is the whole
 * of the remaining signal.
 *
 * That makes the baseline a hazard it was not before. Lint records *informational*
 * incidents into a regenerated baseline exactly as it records errors — the write
 * path filters by issue id, never by severity — and a baselined incident is
 * filtered out of the XML and HTML reports. So a routine
 * `./gradlew :app:updateLintBaseline`, run for some unrelated batch of fixes,
 * would quietly re-absorb every demoted finding, empty the drift report, and
 * leave the build green with nothing anywhere reporting the loss. That is the
 * same silent-suppression failure this project has already paid for once: four
 * such entries had accumulated in the baseline and had to be deleted before the
 * report showed the packages it exists to show.
 *
 * This guard closes the path mechanically instead of by convention: a demoted id
 * may never appear in a committed baseline. Its verdict is a function of the
 * repository contents alone — which is precisely the property the demotion was
 * made to restore, so the guard is legitimate as a gate in a way the checks it
 * protects are not.
 *
 * The scanner is a pure `Map<path, content> -> List<Violation>` transform with no
 * file-system access, so it is trivially unit-testable. The Gradle task in
 * `app/build.gradle.kts` (`verifyLintBaselineOverrides`, wired into `check`)
 * resolves the committed baseline files, reads them, and feeds them here.
 */
object LintBaselineGuard {

    /**
     * The lint issue ids demoted to informational severity — the single
     * declaration site.
     *
     * Both strict modules consume this list directly
     * (`informational += LintBaselineGuard.DEMOTED_ISSUE_IDS` in
     * `app/build.gradle.kts` and `catalog/build.gradle.kts`), which is possible
     * because `buildSrc` output is on every build script's classpath. Declaring
     * it here rather than in the DSL is what makes the guard airtight: a list
     * duplicated per module could grow an entry the guard does not know about,
     * and an unknown id is exactly what this guard cannot report — `scan` only
     * ever names ids it already holds, so drift in that direction would be
     * silent.
     *
     * One copy remains outside this file, in the CI job-summary step of
     * `.github/workflows/check.yml`. It is deliberately not a copy of these ids:
     * the step selects findings by *severity*, so it picks up whatever is
     * demoted without being told.
     */
    val DEMOTED_ISSUE_IDS: List<String> = listOf(
        "GradleDependency",
        "AndroidGradlePluginVersion",
        "NewerVersionAvailable",
        "ExpiringTargetSdkVersion",
    )

    /**
     * A single guard hit: a demoted issue id found inside a committed baseline.
     *
     * @property file Path of the offending baseline, relative to the repository root.
     * @property line 1-indexed line number carrying the `id="..."` attribute.
     * @property issueId The demoted lint issue id that was found.
     */
    data class Violation(
        val file: String,
        val line: Int,
        val issueId: String,
    ) {
        /** Renders the violation in the canonical `path:line: message` failure format. */
        fun format(): String =
            "$file:$line: baselined `$issueId` — delete the enclosing `<issue>` element; " +
                "a demoted check must stay visible in the lint report"
    }

    /**
     * Matches the `id="..."` attribute of a baseline `<issue>` element.
     *
     * Anchored on the attribute rather than on the element so a hit points at the
     * line that identifies the offending entry — the whole enclosing `<issue>`
     * element is what has to go — and so the scanner is immune to how the
     * baseline writer wraps its elements.
     */
    private val ISSUE_ID_ATTRIBUTE = Regex("""\bid="([^"]+)"""")

    /**
     * Scans committed lint baselines for demoted issue ids.
     *
     * @param files Map of repository-root-relative path to full baseline content.
     *   The caller supplies only files that exist; a module without a baseline is
     *   simply absent from the map.
     * @return Every [Violation] found, ordered by file then line, so the failure
     *   message is stable and diff-friendly.
     */
    fun scan(files: Map<String, String>): List<Violation> {
        val demoted = DEMOTED_ISSUE_IDS.toSet()
        val violations = mutableListOf<Violation>()
        for ((path, content) in files) {
            content.lineSequence().forEachIndexed { index, line ->
                ISSUE_ID_ATTRIBUTE.findAll(line).forEach { match ->
                    val issueId = match.groupValues[1]
                    if (issueId in demoted) {
                        violations += Violation(path, index + 1, issueId)
                    }
                }
            }
        }
        return violations.sortedWith(compareBy({ it.file }, { it.line }, { it.issueId }))
    }
}
