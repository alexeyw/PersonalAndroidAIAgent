package app.knotwork.android.domain.engine.structured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ReasoningBlockSplitter].
 *
 * The fixtures are the shapes reasoning models actually emit, not the shape
 * their documentation describes — in particular the orphan closing tag, which
 * is what Qwen3 produces because its chat template opens the block itself.
 */
class ReasoningBlockSplitterTest {

    @Test
    fun `given a complete reasoning block when splitting then only the answer remains`() {
        // Given
        val raw = "<think>\nThe user is home. Keep it warm.\n</think>\n\nПривет, наконец-то ты дома."

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals("Привет, наконец-то ты дома.", split.answer)
        assertEquals("The user is home. Keep it warm.", split.reasoning)
    }

    @Test
    fun `given a closing tag with no opening one when splitting then the preamble is the reasoning`() {
        // Given — Qwen3's chat template emits `<think>` as part of the prompt, so
        // the model continues inside the block and its first tag is the closer.
        // This is the common case; a splitter that only handles matched pairs
        // does nothing at all here.
        val raw = "Okay, the user just said hey. I should be warm.\n</think>\n\nПривет!"

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals("Привет!", split.answer)
        assertEquals("Okay, the user just said hey. I should be warm.", split.reasoning)
    }

    @Test
    fun `given an unterminated block when splitting then the raw text is kept as the answer`() {
        // Given — the generation hit a limit mid-thought. Removing everything
        // would end the run on a blank bubble, which reads as a failure.
        val raw = "<think>\nStill deciding how to phrase this and then the tokens ran"

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals(raw, split.answer)
        assertNull(split.reasoning)
    }

    @Test
    fun `given a block that leaves nothing behind when splitting then the raw text is kept`() {
        // Given — the model produced scratchpad and no answer at all.
        val raw = "<think>\nI have nothing to add here.\n</think>   \n\n "

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals(raw, split.answer)
        assertNull(split.reasoning)
    }

    @Test
    fun `given several blocks when splitting then all are removed and the answer is joined`() {
        // Given
        val raw = "<think>first</think>Hello. <think>second</think>How are you?"

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals("Hello. How are you?", split.answer)
        assertEquals("first\n\nsecond", split.reasoning)
    }

    @Test
    fun `given tags inside a fenced code block when splitting then the answer keeps them`() {
        // Given — someone asking how reasoning models are formatted must get
        // their example back intact.
        val raw = "Reasoning models emit:\n\n```\n<think>deliberation</think>\n```\n\nThat is the format."

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals(raw.trim(), split.answer)
        assertNull(split.reasoning)
    }

    @Test
    fun `given a real block plus a fenced example when splitting then only the real one is removed`() {
        // Given
        val raw = "<think>explain the format</think>\n\nLike this:\n\n```\n<think>x</think>\n```"

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertTrue("the fenced example survives: ${split.answer}", split.answer.contains("<think>x</think>"))
        assertEquals("explain the format", split.reasoning)
    }

    @Test
    fun `given output with no tags when splitting then it is returned unchanged`() {
        // Given
        val raw = "Привет, мой любимый. Ты пришёл, наконец."

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals(raw, split.answer)
        assertNull(split.reasoning)
    }

    @Test
    fun `given uppercase tags when splitting then they are still recognised`() {
        // Given
        val raw = "<THINK>deliberating</THINK>Answer."

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals("Answer.", split.answer)
        assertEquals("deliberating", split.reasoning)
    }

    @Test
    fun `given text before an orphan closing tag and an answer after when splitting then both are placed`() {
        // Given — text that precedes the orphan closer is scratchpad, not answer.
        val raw = "weighing the options\n</think>\nHere is the plan.\n<think>second thought</think>\nDone."

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals("Here is the plan.\n\nDone.", split.answer)
        assertEquals("weighing the options\n\nsecond thought", split.reasoning)
    }

    @Test
    fun `given blank input when splitting then it is returned unchanged`() {
        // Given
        val raw = "   "

        // When
        val split = ReasoningBlockSplitter.split(raw)

        // Then
        assertEquals(raw, split.answer)
        assertNull(split.reasoning)
    }
}
