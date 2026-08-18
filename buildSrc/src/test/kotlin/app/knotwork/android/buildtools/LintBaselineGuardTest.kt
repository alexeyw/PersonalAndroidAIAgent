package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LintBaselineGuard].
 *
 * The regression these pin down is concrete: a routine
 * `./gradlew :app:updateLintBaseline` re-absorbs the demoted version-freshness
 * findings into the baseline, which removes them from the lint report and empties
 * the dependency-drift signal while the build stays green. The fixtures below are
 * verbatim excerpts of what that regeneration writes.
 *
 * Run with `./gradlew -p buildSrc test`.
 */
class LintBaselineGuardTest {

    /** A baseline entry as the lint writer emits it, parameterised by issue id and message. */
    private fun issue(id: String, message: String): String =
        """
        |    <issue
        |        id="$id"
        |        message="$message"
        |        errorLine1="litertlm = &quot;0.15.0&quot;"
        |        errorLine2="           ~~~~~~~~">
        |        <location
        |            file="../gradle/libs.versions.toml"
        |            line="19"
        |            column="12"/>
        |    </issue>
        """.trimMargin()

    @Test
    fun `given baseline without demoted ids when scanned then no violations`() {
        val files = mapOf(
            "app/lint-baseline.xml" to
                issue("UnspecifiedRegisterReceiverFlag", "Missing RECEIVER_EXPORTED flag") + "\n" +
                issue("ObsoleteSdkInt", "This folder configuration is unnecessary") + "\n",
            "catalog/lint-baseline.xml" to "<issues format=\"6\" type=\"baseline\">\n</issues>\n",
        )

        assertTrue(LintBaselineGuard.scan(files).isEmpty())
    }

    @Test
    fun `given regenerated baseline that re-absorbed a demoted finding when scanned then flagged`() {
        val files = mapOf(
            "app/lint-baseline.xml" to
                issue(
                    "GradleDependency",
                    "A newer version of com.google.ai.edge.litertlm:litertlm-android " +
                        "than 0.15.0 is available: 0.16.1",
                ) + "\n",
        )

        val violations = LintBaselineGuard.scan(files)

        assertEquals(
            listOf(LintBaselineGuard.Violation("app/lint-baseline.xml", 2, "GradleDependency")),
            violations,
        )
    }

    @Test
    fun `given every demoted id when scanned then each is flagged exactly once`() {
        val files = LintBaselineGuard.DEMOTED_ISSUE_IDS.associate { id ->
            "module-$id/lint-baseline.xml" to issue(id, "irrelevant") + "\n"
        }

        val violations = LintBaselineGuard.scan(files)

        assertEquals(LintBaselineGuard.DEMOTED_ISSUE_IDS.size, violations.size)
        assertEquals(
            LintBaselineGuard.DEMOTED_ISSUE_IDS.sorted(),
            violations.map { it.issueId }.sorted(),
        )
        assertTrue(violations.all { it.line == 2 })
    }

    @Test
    fun `given several violations when scanned then ordered by file then line`() {
        val files = mapOf(
            "catalog/lint-baseline.xml" to issue("NewerVersionAvailable", "b") + "\n",
            "app/lint-baseline.xml" to
                issue("GradleDependency", "a") + "\n" + issue("GradleDependency", "c") + "\n",
        )

        val violations = LintBaselineGuard.scan(files)

        assertEquals(
            listOf(
                LintBaselineGuard.Violation("app/lint-baseline.xml", 2, "GradleDependency"),
                LintBaselineGuard.Violation("app/lint-baseline.xml", 12, "GradleDependency"),
                LintBaselineGuard.Violation("catalog/lint-baseline.xml", 2, "NewerVersionAvailable"),
            ),
            violations,
        )
    }

    @Test
    fun `given an id that merely contains a demoted id when scanned then not flagged`() {
        val files = mapOf(
            "app/lint-baseline.xml" to
                issue("GradleDependencyExtra", "not the demoted check") + "\n" +
                issue("NotNewerVersionAvailable", "also not it") + "\n",
        )

        assertTrue(LintBaselineGuard.scan(files).isEmpty())
    }

    @Test
    fun `given a message quoting a demoted id when scanned then only the id attribute matches`() {
        val files = mapOf(
            "app/lint-baseline.xml" to
                issue("ObsoleteSdkInt", "see GradleDependency for the version policy") + "\n",
        )

        assertTrue(LintBaselineGuard.scan(files).isEmpty())
    }

    @Test
    fun `given the real committed baselines are clean then the guard passes`() {
        // Mirrors what the Gradle task feeds in: the two committed baselines as
        // they stand after the demoted entries were removed.
        val files = mapOf(
            "app/lint-baseline.xml" to
                issue("RedundantLabel", "Redundant label can be removed") + "\n" +
                issue("ObsoleteSdkInt", "This folder configuration is unnecessary") + "\n" +
                issue("UnspecifiedRegisterReceiverFlag", "Missing RECEIVER_EXPORTED") + "\n" +
                issue("UnspecifiedRegisterReceiverFlag", "Missing RECEIVER_EXPORTED") + "\n" +
                issue("ReportShortcutUsage", "Calling this method indicates use of dynamic shortcuts") + "\n",
        )

        assertTrue(LintBaselineGuard.scan(files).isEmpty())
    }
}
