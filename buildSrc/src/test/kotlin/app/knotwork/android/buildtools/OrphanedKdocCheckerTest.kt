package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [OrphanedKdocChecker].
 *
 * The cases are built around the one distinction that decides whether this
 * check is usable at all: a file-level block followed by a declaration's block
 * is a deliberate and common shape, while the same two blocks inside a body
 * mean a function was inserted between a doc and the thing it documented. A
 * checker that cannot tell them apart would fire on three correct files in this
 * repository and be turned off within a day.
 */
class OrphanedKdocCheckerTest {

    private fun scan(source: String) = OrphanedKdocChecker.scan(mapOf("Sample.kt" to source.trimIndent()))

    @Test
    fun `given a doc block followed by another inside a class then it is reported`() {
        // The real shape: someone added `newThing()` between `oldThing()` and
        // its KDoc, so the first block now documents nothing and `oldThing`
        // silently lost its documentation.
        val violations = scan(
            """
            package sample

            class Sample {
                /**
                 * Documents oldThing, which is no longer below it.
                 */
                /** Documents newThing. */
                fun newThing() = Unit

                fun oldThing() = Unit
            }
            """,
        )

        assertEquals(1, violations.size)
        // `package` 1, blank 2, `class` 3, so the block opens on 4.
        assertEquals(4, violations.first().line)
        assertTrue(violations.first().firstLine.contains("oldThing"))
    }

    @Test
    fun `given a file-level block above the first declaration's block then it is not reported`() {
        // Three files in this repository are written exactly like this. If the
        // check fired here it would be deleted rather than fixed.
        val violations = scan(
            """
            package sample

            import kotlin.math.abs

            /**
             * What this whole file is for.
             */

            /**
             * What this one function is for.
             */
            fun only() = abs(-1)
            """,
        )

        assertTrue("A file-level block is not an orphan: $violations", violations.isEmpty())
    }

    @Test
    fun `given a blank line between the two blocks then it is still reported`() {
        // Kotlin attaches the *last* block regardless of the gap, so a blank
        // line changes nothing about which declaration is documented — and it
        // is exactly what makes the accident hard to see in a diff.
        val violations = scan(
            """
            package sample

            class Sample {
                /** Orphaned. */

                /** Attached. */
                fun thing() = Unit
            }
            """,
        )

        assertEquals(1, violations.size)
    }

    @Test
    fun `given ordinary consecutive declarations each with one block then nothing is reported`() {
        val violations = scan(
            """
            package sample

            class Sample {
                /** First. */
                fun first() = Unit

                /** Second. */
                fun second() = Unit
            }
            """,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given a block followed by annotations and then a declaration then nothing is reported`() {
        // Annotations sit between a KDoc and its function all over the DI
        // modules. Treating an annotation as "the body has begun" would report
        // every provider in the codebase.
        val violations = scan(
            """
            package sample

            /**
             * The module.
             */
            object Module {
                /**
                 * The provider.
                 */
                @Provides
                @Singleton
                fun provide(): String = ""
            }
            """,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given a single-line block followed by another then it is reported`() {
        val violations = scan(
            """
            package sample

            class Sample {
                /** Orphaned one-liner. */
                /** Attached. */
                fun thing() = Unit
            }
            """,
        )

        assertEquals(1, violations.size)
        assertTrue(violations.first().firstLine.contains("Orphaned one-liner"))
    }

    @Test
    fun `given an ordinary block comment between two doc blocks then the first is still orphaned`() {
        // A plain block comment is not KDoc and documents nothing, so it cannot
        // rescue the block above it — the declaration still takes the last doc
        // block, and the first one is still attached to nothing.
        val violations = scan(
            """
            package sample

            class Sample {
                /** Orphaned. */
                /* Just a note, not documentation. */
                /** Attached. */
                fun thing() = Unit
            }
            """,
        )

        assertEquals(1, violations.size)
    }

    @Test
    fun `given an unterminated block then it reports what it found rather than throwing`() {
        // A missing `*/` is a compiler error with a far better message than
        // anything this check could produce; it must not turn into a crash here.
        val violations = scan(
            """
            package sample

            class Sample {
                /**
                 * Never closed
                fun thing() = Unit
            """,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `given several files then each is scanned independently`() {
        val violations = OrphanedKdocChecker.scan(
            mapOf(
                "A.kt" to "package a\n\nclass A {\n    /** One. */\n    /** Two. */\n    fun a() = Unit\n}\n",
                "B.kt" to "package b\n\nclass B {\n    /** Only. */\n    fun b() = Unit\n}\n",
            ),
        )

        assertEquals(1, violations.size)
        assertEquals("A.kt", violations.first().file)
    }
}
