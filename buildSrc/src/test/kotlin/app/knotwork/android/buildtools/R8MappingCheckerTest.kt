package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [R8MappingChecker].
 *
 * The fixtures reproduce the shape of a real R8 `mapping.txt` — unindented class
 * lines, indented member lines, comment lines — and both outcomes that matter:
 * the protected package kept its names (the fixed build), and the protected
 * package was obfuscated away (the build that crashed on the first message).
 * Run with `./gradlew -p buildSrc test`.
 */
class R8MappingCheckerTest {

    private val floggerPrefix = "com.google.common.flogger."

    @Test
    fun `given an identity-mapped package when verified then no violations`() {
        val mapping = """
            # compiler: R8
            com.google.common.flogger.FluentLogger -> com.google.common.flogger.FluentLogger:
                1:1:void <init>() -> <init>
            com.google.common.flogger.backend.Platform -> com.google.common.flogger.backend.Platform:
            app.knotwork.android.MainActivity -> a.b.c:
        """.trimIndent()

        assertTrue(R8MappingChecker.verifyIdentityMapping(mapping, floggerPrefix).isEmpty())
    }

    @Test
    fun `given an obfuscated protected class when verified then it is flagged with both names`() {
        // This is the mapping that shipped the crash: flogger renamed, so its
        // own stack-walk anchor no longer matched.
        val mapping = """
            com.google.common.flogger.FluentLogger -> qm2:
            com.google.common.flogger.backend.Platform -> com.google.common.flogger.backend.Platform:
        """.trimIndent()

        val violations = R8MappingChecker.verifyIdentityMapping(mapping, floggerPrefix)

        assertEquals(1, violations.size)
        assertEquals("com.google.common.flogger.FluentLogger", violations.single().originalName)
        assertEquals("qm2", violations.single().mappedName)
        assertTrue(violations.single().format().contains("no longer pins it"))
    }

    @Test
    fun `given a mapping without the protected package when verified then absence is a violation`() {
        // A vacuous pass is the dangerous case: the dependency was dropped or the
        // whole package was shrunk away, and an "all identity-mapped" answer over
        // zero classes would hide it.
        val mapping = "app.knotwork.android.MainActivity -> a.b.c:"

        val violations = R8MappingChecker.verifyIdentityMapping(mapping, floggerPrefix)

        assertEquals(1, violations.size)
        assertEquals(floggerPrefix, violations.single().originalName)
        assertEquals(null, violations.single().mappedName)
        assertTrue(violations.single().format().contains("no class from this package"))
    }

    @Test
    fun `given indented member lines when verified then they are not parsed as classes`() {
        // A member line's arrow shape resembles a class line; misparsing one as a
        // class would produce a bogus violation.
        val mapping = """
            com.google.common.flogger.FluentLogger -> com.google.common.flogger.FluentLogger:
                1:1:com.google.common.flogger.FluentLogger forEnclosingClass() -> a
        """.trimIndent()

        assertTrue(R8MappingChecker.verifyIdentityMapping(mapping, floggerPrefix).isEmpty())
    }
}
