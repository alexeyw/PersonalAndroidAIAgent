package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the filter helper backing
 * [PromptPresetPickerDialog]. The Composable surface itself is exercised
 * via the instrumented harness; the filter rule is broken out into
 * [filterPresets] so the case matrix (search / tags / both / neither) can
 * be unit-tested without spinning up Compose.
 */
class PromptPresetPickerDialogTest {

    private val a = preset(id = "a", name = "Concise assistant", tags = listOf("concise", "general"))
    private val b = preset(id = "b", name = "JSON formatter", tags = listOf("json", "structured"))
    private val c = preset(id = "c", name = "Reasoning chain", tags = listOf("reasoning", "concise"))

    @Test
    fun `given blank query and no tags when filter then returns all`() {
        val result = filterPresets(listOf(a, b, c), query = "  ", selectedTags = emptySet())
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun `given query when filter then matches name case-insensitively`() {
        val result = filterPresets(listOf(a, b, c), query = "json", selectedTags = emptySet())
        assertEquals(listOf(b), result)
    }

    @Test
    fun `given single tag when filter then keeps presets carrying that tag`() {
        val result = filterPresets(listOf(a, b, c), query = "", selectedTags = setOf("concise"))
        assertEquals(listOf(a, c), result)
    }

    @Test
    fun `given multiple tags when filter then requires all tags present`() {
        val result = filterPresets(
            presets = listOf(a, b, c),
            query = "",
            selectedTags = setOf("concise", "reasoning"),
        )
        assertEquals(listOf(c), result)
    }

    @Test
    fun `given query and tag when filter then both must match`() {
        val result = filterPresets(
            presets = listOf(a, b, c),
            query = "chain",
            selectedTags = setOf("concise"),
        )
        assertEquals(listOf(c), result)
    }

    @Test
    fun `given tag with mismatched case when filter then still matches`() {
        val result = filterPresets(
            presets = listOf(a),
            query = "",
            selectedTags = setOf("CONCISE"),
        )
        assertEquals(listOf(a), result)
    }

    @Test
    fun `buildTagChips emits leading All chip with unfiltered source count`() {
        val chips = buildTagChips(
            sourceListCount = 3,
            tagCounts = mapOf("concise" to 2, "json" to 1),
            allLabel = "All",
        )
        // The first chip is the leading "All" chip (tag == null) and its count
        // mirrors the unfiltered source list — that's what the mockup shows.
        assertEquals(null, chips.first().tag)
        assertEquals("All", chips.first().label)
        assertEquals(3, chips.first().count)
        // Remaining chips are alphabetical and carry their own per-tag count.
        assertEquals(listOf("concise", "json"), chips.drop(1).map { it.tag })
        assertEquals(listOf(2, 1), chips.drop(1).map { it.count })
    }

    @Test
    fun `estimateTokens approximates chars per four`() {
        // GPT-style rule of thumb. The exact value isn't important — what matters
        // is that the helper rounds DOWN integer division so a 3-char prompt
        // reads as 0 tokens (we don't lie about a sub-token prompt being 1 tok).
        assertEquals(0, estimateTokens(""))
        assertEquals(0, estimateTokens("hi"))
        assertEquals(1, estimateTokens("abcd"))
        assertEquals(21, estimateTokens("x".repeat(84)))
    }

    private fun preset(id: String, name: String, tags: List<String>): PromptPreset = PromptPreset(
        id = id,
        name = name,
        description = "",
        nodeType = NodeType.LITE_RT,
        systemPrompt = "You are $name.",
        tags = tags,
        isBundled = true,
    )
}
