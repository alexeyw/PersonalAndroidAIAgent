package app.knotwork.android.buildtools

/**
 * Pure rendering half of the external-link report.
 *
 * External `http` links are reported, never gated. Their verdict depends on
 * somebody else's server rather than on the commit under review, and a check
 * whose answer can change while the repository does not has no business among
 * the conditions for merging — the same reasoning that demoted the dependency
 * version-freshness checks to informational.
 *
 * Grouping and rendering live here so they are unit-tested; the Gradle task
 * keeps only the part that cannot be pure — the network round-trip.
 */
object ExternalLinkReport {

    /**
     * One distinct URL and every place it is written.
     *
     * @property url The absolute URL.
     * @property references `path:line` of each occurrence, in document order.
     */
    data class Target(val url: String, val references: List<String>)

    /**
     * What probing one URL produced.
     *
     * @property url The absolute URL.
     * @property ok Whether the server answered in a way a reader would accept.
     * @property status Short human-readable outcome — a status code, or the
     *   failure's class.
     * @property references `path:line` of each occurrence.
     */
    data class Outcome(val url: String, val ok: Boolean, val status: String, val references: List<String>)

    /**
     * Collapses the extracted links into one entry per distinct URL.
     *
     * A single URL repeated across the documentation should be fetched once and
     * reported once, carrying every place it appears.
     *
     * @param links Every external link found by [DocLinkChecker].
     * @return One target per distinct URL, ordered by URL.
     */
    fun targetsOf(links: List<DocLinkChecker.ExternalLink>): List<Target> =
        links.groupBy { it.url }
            .map { (url, occurrences) -> Target(url, occurrences.map { "${it.file}:${it.line}" }) }
            .sortedBy { it.url }

    /**
     * Renders the report as Markdown, suitable for both the console and a CI
     * job summary.
     *
     * @param outcomes Every probed URL.
     * @return The report. It always states the totals — a report that lists no
     *   failures because it probed nothing must not read like a clean bill.
     */
    fun render(outcomes: List<Outcome>): String {
        val failures = outcomes.filterNot { it.ok }
        return buildString {
            appendLine("# External documentation links")
            appendLine()
            appendLine("Probed ${outcomes.size} distinct URL(s); ${failures.size} did not answer.")
            appendLine()
            appendLine(
                "This is a report, not a gate: an external link's verdict depends on somebody else's " +
                    "server, so it never fails the build. Fix or replace what is listed below.",
            )
            if (failures.isEmpty()) return@buildString
            appendLine()
            appendLine("| URL | Outcome | Written at |")
            appendLine("|---|---|---|")
            for (failure in failures) {
                appendLine("| ${failure.url} | ${failure.status} | ${failure.references.joinToString(", ")} |")
            }
        }
    }
}
