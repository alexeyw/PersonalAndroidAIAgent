package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VersionSourcesChecker].
 *
 * Each hand-written copy of the version number is broken on its own, so a
 * regression in one detector cannot hide behind another. Run with
 * `./gradlew -p buildSrc test`.
 */
class VersionSourcesCheckerTest {

    private val readme = """
        # Knotwork

        ![Version](https://img.shields.io/badge/version-0.8.0-orange.svg)

        ## Pre-release notice

        This project is currently at **version 0.8.0** and is published for review.

        Updating a debug-signed install from before 0.7.0 requires a reinstall.
    """.trimIndent()

    private val changelog = """
        # Changelog

        ## [Unreleased]

        ### Added

        - Something.

        ## [0.8.0] - 2026-08-21

        - Shipped.

        [Unreleased]: https://github.com/alexeyw/knotwork/compare/v0.8.0...HEAD
        [0.8.0]: https://github.com/alexeyw/knotwork/compare/v0.7.3...v0.8.0
    """.trimIndent()

    private val security = """
        # Security Policy

        The project is currently a **pre-release (0.8.0)** and is published for review.

        ## Supported Versions

        Fixes land on the current `0.8.x` line and on the latest commit on `main`.

        | Version            | Supported          |
        |--------------------|--------------------|
        | `0.8.x` (latest)   | :white_check_mark: |
        | `< 0.8.0`          | :x:                |
    """.trimIndent()

    @Test
    fun `given every source agreeing when checked then no violations`() {
        assertTrue(VersionSourcesChecker.check("0.8.0", readme, changelog, security).isEmpty())
    }

    @Test
    fun `given a stale README badge when checked then reported with both values`() {
        val stale = readme.replace("version-0.8.0", "version-0.7.3")

        val violations = VersionSourcesChecker.check("0.8.0", stale, changelog, security)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("README.md"))
        assertTrue(violations[0].contains("`0.7.3`"))
        assertTrue(violations[0].contains("`0.8.0`"))
    }

    @Test
    fun `given a changelog heading behind the build when checked then reported`() {
        val violations = VersionSourcesChecker.check(
            "0.9.0",
            readme.replace("0.8.0", "0.9.0"),
            changelog,
            security.replace("0.8", "0.9"),
        )

        assertTrue(violations.any { it.contains("topmost released heading") })
    }

    @Test
    fun `given a stale unreleased compare link when checked then reported`() {
        val stale = changelog.replace("compare/v0.8.0...HEAD", "compare/v0.7.3...HEAD")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale, security)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("compare link"))
    }

    @Test
    fun `given a release heading with no link definition when checked then reported`() {
        val stale = changelog.replace("[0.8.0]: https://github.com/alexeyw/knotwork/compare/v0.7.3...v0.8.0", "")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale, security)

        assertTrue(violations.any { it.contains("no link definition") })
    }

    @Test
    fun `given a link definition naming another tag when checked then reported`() {
        val stale = changelog.replace("v0.7.3...v0.8.0", "v0.7.3...v0.8.1")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale, security)

        assertTrue(violations.any { it.contains("does not name tag `v0.8.0`") })
    }

    @Test
    fun `given a missing badge when checked then the absent source is a violation`() {
        val violations = VersionSourcesChecker.check("0.8.0", "# Knotwork\n", changelog, security)

        assertTrue(violations.any { it.contains("could not be found") })
    }

    @Test
    fun `given no declared versionName when checked then reported once`() {
        assertEquals(1, VersionSourcesChecker.check("  ", readme, changelog, security).size)
    }

    // ── The two sources added after the guard had already shipped. Both are
    // regressions that actually happened at the next release cut, not
    // hypotheticals: the README prose and SECURITY.md were left at the previous
    // version while the four originally-guarded copies were updated correctly.

    @Test
    fun `given the README prose left behind when checked then reported on its own`() {
        val stale = readme.replace("currently at **version 0.8.0**", "currently at **version 0.7.3**")

        val violations = VersionSourcesChecker.check("0.8.0", stale, changelog, security)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("Pre-release"))
        assertTrue(violations[0].contains("`0.7.3`"))
    }

    @Test
    fun `given the README prose missing when checked then the absent source is a violation`() {
        val stale = readme.replace("This project is currently at **version 0.8.0** and is published for review.", "")

        val violations = VersionSourcesChecker.check("0.8.0", stale, changelog, security)

        assertTrue(violations.any { it.contains("could not be found") })
    }

    @Test
    fun `given the SECURITY prose left behind when checked then reported`() {
        val stale = security.replace("pre-release (0.8.0)", "pre-release (0.7.3)")

        val violations = VersionSourcesChecker.check("0.8.0", readme, changelog, stale)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("SECURITY.md"))
        assertTrue(violations[0].contains("`0.7.3`"))
    }

    @Test
    fun `given a stale SECURITY supported-versions table when checked then reported`() {
        val stale = security.replace("| `0.8.x` (latest)", "| `0.7.x` (latest)")

        val violations = VersionSourcesChecker.check("0.8.0", readme, changelog, stale)

        assertTrue(violations.any { it.contains("`0.7.x`") })
    }

    @Test
    fun `given the SECURITY minor line matching the build when checked then it is not a violation`() {
        // `0.8.x` is the supported *line*, not a stale full version: the rule has
        // to accept it, or the document could never be written correctly.
        assertTrue(VersionSourcesChecker.check("0.8.0", readme, changelog, security).isEmpty())
    }

    @Test
    fun `given several stale versions in SECURITY when checked then each is reported once`() {
        val stale = security
            .replace("pre-release (0.8.0)", "pre-release (0.7.3)")
            .replace("| `< 0.8.0`", "| `< 0.6.0`")

        val violations = VersionSourcesChecker.check("0.8.0", readme, changelog, stale)

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("`0.7.3`") })
        assertTrue(violations.any { it.contains("`0.6.0`") })
    }

    @Test
    fun `given SECURITY naming no version at all when checked then reported`() {
        val violations = VersionSourcesChecker.check("0.8.0", readme, changelog, "# Security Policy\n")

        assertTrue(violations.any { it.contains("names no version at all") })
    }
}
