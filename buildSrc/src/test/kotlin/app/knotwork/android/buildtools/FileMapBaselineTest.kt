package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Test

/** Coverage for [FileMapBaseline], the documentation ratchet. */
class FileMapBaselineTest {

    @Test
    fun `given comments and blank lines when parsing then only the counts are read`() {
        val text = "# a comment\n\napp-main.undescribed = 3\n  app-test.no-kdoc-seed=71\n"

        assertEquals(mapOf("app-main.undescribed" to 3, "app-test.no-kdoc-seed" to 71), FileMapBaseline.parse(text))
    }

    @Test(expected = FileMapBaseline.ParseException::class)
    fun `given a value that is not a number when parsing then it fails`() {
        FileMapBaseline.parse("app-main.undescribed=some\n")
    }

    @Test(expected = FileMapBaseline.ParseException::class)
    fun `given a line without a separator when parsing then it fails`() {
        FileMapBaseline.parse("app-main.undescribed\n")
    }

    @Test
    fun `given rendered counts when parsing them back then the values round-trip`() {
        val counts = mapOf("b.no-kdoc-seed" to 0, "a.undescribed" to 12)

        assertEquals(counts, FileMapBaseline.parse(FileMapBaseline.render(counts)))
    }

    @Test
    fun `given counts in any order when rendering then the file has one canonical form`() {
        val one = FileMapBaseline.render(mapOf("b" to 1, "a" to 2))
        val other = FileMapBaseline.render(mapOf("a" to 2, "b" to 1))

        assertEquals(one, other)
    }

    @Test
    fun `given a count above its record when checking then it is a violation`() {
        val violations = FileMapBaseline.violations(mapOf("a" to 4), mapOf("a" to 3))

        assertEquals(listOf("a: 4, recorded 3"), violations)
    }

    @Test
    fun `given a count at or below its record when checking then it is not a violation`() {
        assertEquals(
            emptyList<String>(),
            FileMapBaseline.violations(mapOf("a" to 3, "b" to 1), mapOf("a" to 3, "b" to 9)),
        )
    }

    @Test
    fun `given a measured key absent from the record when checking then a non-zero count is a violation`() {
        assertEquals(listOf("fresh: 2, recorded 0"), FileMapBaseline.violations(mapOf("fresh" to 2), emptyMap()))
        assertEquals(emptyList<String>(), FileMapBaseline.violations(mapOf("fresh" to 0), emptyMap()))
    }

    @Test
    fun `given an improvement when lowering then the record follows it down`() {
        assertEquals(mapOf("a" to 1), FileMapBaseline.lowered(mapOf("a" to 1), mapOf("a" to 5)))
    }

    @Test
    fun `given a regression when lowering then the record is left where it was`() {
        // The ratchet only turns one way: raising a number has to be a
        // deliberate edit, never a side effect of running the generator.
        assertEquals(mapOf("a" to 5), FileMapBaseline.lowered(mapOf("a" to 9), mapOf("a" to 5)))
    }

    @Test
    fun `given a key that is no longer measured when lowering then it leaves the record`() {
        assertEquals(mapOf("a" to 1), FileMapBaseline.lowered(mapOf("a" to 1), mapOf("a" to 1, "gone" to 4)))
    }

    @Test
    fun `given a key measured for the first time when lowering then it enters at its measured value`() {
        assertEquals(mapOf("fresh" to 7), FileMapBaseline.lowered(mapOf("fresh" to 7), emptyMap()))
    }
}
