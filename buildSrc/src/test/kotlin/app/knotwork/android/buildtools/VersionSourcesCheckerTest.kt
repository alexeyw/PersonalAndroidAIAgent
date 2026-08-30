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

    @Test
    fun `given every source agreeing when checked then no violations`() {
        assertTrue(VersionSourcesChecker.check("0.8.0", readme, changelog).isEmpty())
    }

    @Test
    fun `given a stale README badge when checked then reported with both values`() {
        val stale = readme.replace("version-0.8.0", "version-0.7.3")

        val violations = VersionSourcesChecker.check("0.8.0", stale, changelog)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("README.md"))
        assertTrue(violations[0].contains("`0.7.3`"))
        assertTrue(violations[0].contains("`0.8.0`"))
    }

    @Test
    fun `given a changelog heading behind the build when checked then reported`() {
        val violations = VersionSourcesChecker.check("0.9.0", readme.replace("0.8.0", "0.9.0"), changelog)

        assertTrue(violations.any { it.contains("topmost released heading") })
    }

    @Test
    fun `given a stale unreleased compare link when checked then reported`() {
        val stale = changelog.replace("compare/v0.8.0...HEAD", "compare/v0.7.3...HEAD")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale)

        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("compare link"))
    }

    @Test
    fun `given a release heading with no link definition when checked then reported`() {
        val stale = changelog.replace("[0.8.0]: https://github.com/alexeyw/knotwork/compare/v0.7.3...v0.8.0", "")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale)

        assertTrue(violations.any { it.contains("no link definition") })
    }

    @Test
    fun `given a link definition naming another tag when checked then reported`() {
        val stale = changelog.replace("v0.7.3...v0.8.0", "v0.7.3...v0.8.1")

        val violations = VersionSourcesChecker.check("0.8.0", readme, stale)

        assertTrue(violations.any { it.contains("does not name tag `v0.8.0`") })
    }

    @Test
    fun `given a missing badge when checked then the absent source is a violation`() {
        val violations = VersionSourcesChecker.check("0.8.0", "# Knotwork\n", changelog)

        assertTrue(violations.any { it.contains("could not be found") })
    }

    @Test
    fun `given no declared versionName when checked then reported once`() {
        assertEquals(1, VersionSourcesChecker.check("  ", readme, changelog).size)
    }
}
