package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [StoreListingLengthChecker].
 *
 * The cases are chosen around the boundary rather than around obviously-huge
 * inputs, because the whole reason this gate exists is that the real files sit
 * a handful of characters under their ceilings — one of them by two. A checker
 * that only catches gross overruns would not have caught anything that has
 * actually threatened a release.
 */
class StoreListingLengthCheckerTest {

    @Test
    fun `given a field exactly at its limit then it passes`() {
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/title.txt" to "x".repeat(30)),
        )

        assertTrue("A field at the limit is accepted by Play", violations.isEmpty())
    }

    @Test
    fun `given a field one character over its limit then it fails`() {
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/title.txt" to "x".repeat(31)),
        )

        assertEquals(1, violations.size)
        assertEquals(30, violations.first().limit)
        assertEquals(31, violations.first().length)
    }

    @Test
    fun `given a trailing newline then it does not count towards the limit`() {
        // Every file in the tree ends with one and Play does not receive it, so
        // counting it would cost a character of margin the text never spent.
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/title.txt" to "x".repeat(30) + "\n"),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given text outside the basic plane then it is counted in code points`() {
        // 30 emoji are 30 characters to Play and 60 UTF-16 units to Kotlin.
        // Counting the wrong unit would reject a title that fits.
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/title.txt" to "🧶".repeat(30)),
        )

        assertTrue("Code points, not UTF-16 units", violations.isEmpty())
    }

    @Test
    fun `given a changelog then it is matched by its directory rather than its name`() {
        // Changelog files are named after the version code, so the field cannot
        // be recognised from the file name the way the other three are.
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/changelogs/42.txt" to "x".repeat(501)),
        )

        assertEquals(1, violations.size)
        assertEquals(StoreListingLengthChecker.CHANGELOG_LIMIT, violations.first().limit)
        assertTrue(violations.first().label.contains("release notes"))
    }

    @Test
    fun `given every field over its limit then every one is reported`() {
        // All of them, not the first: a release blocked twice in a row because
        // the gate named one field at a time is a gate people learn to distrust.
        val violations = StoreListingLengthChecker.scan(
            mapOf(
                "fastlane/metadata/android/en-US/title.txt" to "x".repeat(31),
                "fastlane/metadata/android/en-US/short_description.txt" to "x".repeat(81),
                "fastlane/metadata/android/ru-RU/full_description.txt" to "x".repeat(4_001),
                "fastlane/metadata/android/ru-RU/changelogs/42.txt" to "x".repeat(501),
            ),
        )

        assertEquals(4, violations.size)
    }

    @Test
    fun `given a file that is not a listing field then it is ignored`() {
        val violations = StoreListingLengthChecker.scan(
            mapOf("fastlane/metadata/android/en-US/video.txt" to "x".repeat(9_999)),
        )

        assertTrue("Unknown files must not be guessed at", violations.isEmpty())
    }
}
