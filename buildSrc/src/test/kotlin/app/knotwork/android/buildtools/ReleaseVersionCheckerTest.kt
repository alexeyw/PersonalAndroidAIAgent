package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ReleaseVersionChecker].
 *
 * The cases cover both directions of the drift this guard exists to catch (tag
 * ahead of the build, tag behind it), the tag shapes a maintainer plausibly
 * types by mistake, and the pre-release suffix that must keep working.
 * Run with `./gradlew :buildSrc:test`.
 */
class ReleaseVersionCheckerTest {

    @Test
    fun `given a tag matching the declared version when verified then no error`() {
        assertNull(ReleaseVersionChecker.verify(tag = "v0.7.0", declaredVersionName = "0.7.0"))
    }

    @Test
    fun `given a pre-release tag matching the declared version when verified then no error`() {
        assertNull(ReleaseVersionChecker.verify(tag = "v0.7.0-rc1", declaredVersionName = "0.7.0-rc1"))
    }

    @Test
    fun `given surrounding whitespace when verified then it is ignored`() {
        assertNull(ReleaseVersionChecker.verify(tag = " v0.7.0\n", declaredVersionName = "0.7.0 "))
    }

    @Test
    fun `given a tag ahead of the declared version when verified then both values are reported`() {
        val error = ReleaseVersionChecker.verify(tag = "v0.7.0", declaredVersionName = "0.6.0")

        assertTrue(
            "expected the message to name both versions, was: $error",
            error != null && error.contains("`0.7.0`") && error.contains("`0.6.0`"),
        )
    }

    @Test
    fun `given a tag behind the declared version when verified then it is rejected too`() {
        // The reverse drift is just as wrong: it would republish an already-used
        // version number with different bytes.
        assertTrue(ReleaseVersionChecker.verify(tag = "v0.6.0", declaredVersionName = "0.7.0") != null)
    }

    @Test
    fun `given a pre-release tag against a stable declared version when verified then it is rejected`() {
        assertTrue(ReleaseVersionChecker.verify(tag = "v0.7.0-rc1", declaredVersionName = "0.7.0") != null)
    }

    @Test
    fun `given a tag without the v prefix when verified then it is rejected as malformed`() {
        val error = ReleaseVersionChecker.verify(tag = "0.7.0", declaredVersionName = "0.7.0")

        assertTrue(
            "expected a malformed-tag message, was: $error",
            error != null && error.contains("not a valid release tag"),
        )
    }

    @Test
    fun `given a two-component tag when verified then it is rejected as malformed`() {
        assertTrue(ReleaseVersionChecker.verify(tag = "v0.7", declaredVersionName = "0.7") != null)
    }

    @Test
    fun `given a non-version tag when verified then it is rejected as malformed`() {
        assertTrue(ReleaseVersionChecker.verify(tag = "phase-40-release", declaredVersionName = "0.7.0") != null)
    }

    @Test
    fun `given a blank declared version when verified then the build config is named as the fix`() {
        val error = ReleaseVersionChecker.verify(tag = "v0.7.0", declaredVersionName = "   ")

        assertTrue(
            "expected the message to point at versionName, was: $error",
            error != null && error.contains("versionName"),
        )
    }

    @Test
    fun `given a well-formed tag when the version name is extracted then the v prefix is stripped`() {
        assertEquals("1.2.3", ReleaseVersionChecker.versionNameFromTag("v1.2.3"))
        assertEquals("1.2.3-beta.2", ReleaseVersionChecker.versionNameFromTag("v1.2.3-beta.2"))
    }

    @Test
    fun `given a malformed tag when the version name is extracted then it is null`() {
        assertNull(ReleaseVersionChecker.versionNameFromTag("v1.2.3-"))
        assertNull(ReleaseVersionChecker.versionNameFromTag("vX.Y.Z"))
        assertNull(ReleaseVersionChecker.versionNameFromTag(""))
    }
}
