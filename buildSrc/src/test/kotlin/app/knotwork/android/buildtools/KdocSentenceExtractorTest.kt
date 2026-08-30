package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for [KdocSentenceExtractor].
 *
 * The cases that matter here are the ones where a naive parser answers
 * *plausibly* rather than not at all: the wrong declaration's KDoc reads like a
 * real description, so nothing downstream can catch it. Each such case is
 * pinned.
 */
class KdocSentenceExtractorTest {

    @Test
    fun `given a name-matching declaration when extracting then its KDoc wins over an earlier one`() {
        val source = """
            package p

            /** Pending-dot pulse period, matching StatusPill. */
            private const val PENDING_PULSE_MS = 1400

            /** Renders the trigger detail surface. */
            @Composable
            fun TriggerDetailContent() = Unit
        """.trimIndent()

        assertEquals(
            "Renders the trigger detail surface.",
            KdocSentenceExtractor.firstSentence("TriggerDetailContent.kt", source),
        )
    }

    @Test
    fun `given an annotation with a trailing line comment when extracting then the declaration is still found`() {
        // The regression this guards: `@Suppress("…") // reason` between the
        // KDoc and the declaration made the parser report the file as
        // undocumented, silently, for a whole family of catalog components.
        val source = """
            package p

            /** Knotwork secondary button. */
            @Suppress("LongParameterList") // Brand-stable public API.
            @Composable
            fun KnotworkSecondaryButton() = Unit
        """.trimIndent()

        assertEquals(
            "Knotwork secondary button.",
            KdocSentenceExtractor.firstSentence("KnotworkSecondaryButton.kt", source),
        )
    }

    @Test
    fun `given a nested block comment before the declaration when extracting then it is skipped`() {
        val source = """
            package p

            /** The thing. */
            /* outer /* inner */ still outer */
            class Thing
        """.trimIndent()

        assertEquals("The thing.", KdocSentenceExtractor.firstSentence("Thing.kt", source))
    }

    @Test
    fun `given no name match and a single documented declaration when extracting then that one is used`() {
        val source = """
            package p

            /** Deterministic fixtures for the preview matrix. */
            val fixtures = emptyList<String>()
        """.trimIndent()

        assertEquals(
            "Deterministic fixtures for the preview matrix.",
            KdocSentenceExtractor.firstSentence("ChatHomePreviewData.kt", source),
        )
    }

    @Test
    fun `given several documented declarations and no name match when extracting then nothing is returned`() {
        val source = """
            package p

            /** First thing. */
            class First

            /** Second thing. */
            class Second
        """.trimIndent()

        assertNull(KdocSentenceExtractor.firstSentence("Models.kt", source))
    }

    @Test
    fun `given overloads sharing the file name when extracting then the first is used`() {
        val source = """
            package p

            /** Primary form. */
            fun Widget(text: String) = text

            /** Slot form. */
            fun Widget(content: () -> Unit) = content
        """.trimIndent()

        assertEquals("Primary form.", KdocSentenceExtractor.firstSentence("Widget.kt", source))
    }

    @Test
    fun `given a file with no KDoc when extracting then nothing is returned`() {
        assertNull(KdocSentenceExtractor.firstSentence("Bare.kt", "package p\n\nclass Bare\n"))
    }

    @Test
    fun `given an extension property whose name differs in case when extracting then it still matches`() {
        val source = """
            package p

            /** `I.undo` glyph (undo) — single-stroke icon family. */
            val I.undo: ImageVector get() = builder()
        """.trimIndent()

        assertEquals(
            "`I.undo` glyph (undo) — single-stroke icon family.",
            KdocSentenceExtractor.firstSentence("Undo.kt", source),
        )
    }

    @Test
    fun `given a generic extension function when extracting then the receiver is not mistaken for the name`() {
        val source = """
            package p

            /** Joins the payloads. */
            fun <T> List<T>.joined(): String = ""
        """.trimIndent()

        assertEquals("Joins the payloads.", KdocSentenceExtractor.firstSentence("joined.kt", source))
    }

    @Test
    fun `given a backtick-quoted declaration name when extracting then the quotes are not part of the name`() {
        val source = """
            package p

            /** Reads odd names. */
            fun `odd name`() = Unit
        """.trimIndent()

        assertEquals("Reads odd names.", KdocSentenceExtractor.firstSentence("odd name.kt", source))
    }

    @Test
    fun `given a dotted identifier in the first sentence when extracting then it does not end the sentence`() {
        val source = """
            package p

            /** Forwards Log.WARN records to the sink. Everything else is dropped. */
            class Tree
        """.trimIndent()

        assertEquals(
            "Forwards Log.WARN records to the sink.",
            KdocSentenceExtractor.firstSentence("Tree.kt", source),
        )
    }

    @Test
    fun `given an abbreviation in the first sentence when extracting then it does not end the sentence`() {
        val source = """
            package p

            /** Handles the destructive cases, e.g. delete. And nothing else. */
            class Gate
        """.trimIndent()

        assertEquals(
            "Handles the destructive cases, e.g. delete.",
            KdocSentenceExtractor.firstSentence("Gate.kt", source),
        )
    }

    @Test
    fun `given a period inside a code span when extracting then the sentence continues past it`() {
        val source = """
            package p

            /** Wires `Settings.Global` into the theme. Second sentence. */
            class Wiring
        """.trimIndent()

        assertEquals(
            "Wires `Settings.Global` into the theme.",
            KdocSentenceExtractor.firstSentence("Wiring.kt", source),
        )
    }

    @Test
    fun `given a KDoc reference when extracting then it is rewritten as a code span`() {
        val source = """
            package p

            /** Data-layer impl of [CloudLlmClientFactory]. */
            class KoogClientFactory
        """.trimIndent()

        assertEquals(
            "Data-layer impl of `CloudLlmClientFactory`.",
            KdocSentenceExtractor.firstSentence("KoogClientFactory.kt", source),
        )
    }

    @Test
    fun `given a KDoc whose description spans lines when extracting then it is flattened`() {
        val source = """
            package p

            /**
             * Refuses unencrypted requests to public hosts
             * on the shared client.
             *
             * The manifest permits cleartext app-wide.
             */
            class Guard
        """.trimIndent()

        assertEquals(
            "Refuses unencrypted requests to public hosts on the shared client.",
            KdocSentenceExtractor.firstSentence("Guard.kt", source),
        )
    }

    @Test
    fun `given a KDoc that opens with a block tag when extracting then nothing is returned`() {
        val source = """
            package p

            /**
             * @param value the value
             */
            fun Tagged(value: Int) = value
        """.trimIndent()

        assertNull(KdocSentenceExtractor.firstSentence("Tagged.kt", source))
    }

    @Test
    fun `given an indented member KDoc when extracting then it is not treated as top level`() {
        val source = """
            package p

            class Outer {
                /** A member, not the file. */
                fun member() = Unit
            }
        """.trimIndent()

        assertNull(KdocSentenceExtractor.firstSentence("Outer.kt", source))
    }
}
