package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.RunOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MemoryRetrievalQueryResolver] — the `RunOrigin` × declared-query
 * matrix of the retrieval-key contract (`DESCRIPTION.md` §6.10.1).
 */
class MemoryRetrievalQueryResolverTest {

    private companion object {
        const val USER_PROMPT = "write the evening journal entry"
        const val DECLARED = "evening journal entries, mood and highlights"
        const val NODE_INPUT = "today: shipped the trigger journal, ran 8 km"
    }

    private fun resolve(
        origin: RunOrigin,
        declaredQuery: String? = null,
        nodeInput: String = NODE_INPUT,
        userPrompt: String = USER_PROMPT,
    ) = MemoryRetrievalQueryResolver.resolve(
        origin = origin,
        declaredQuery = declaredQuery,
        nodeInput = nodeInput,
        userPrompt = userPrompt,
    )

    /* ---------------- Interactive origins: unchanged behaviour ---------------- */

    @Test
    fun `given chat origin when resolving then keys off the user prompt`() {
        val result = resolve(RunOrigin.CHAT)

        assertEquals(USER_PROMPT, result.text)
        assertEquals(RetrievalQuerySource.USER_PROMPT, result.source)
    }

    @Test
    fun `given share origin when resolving then keys off the user prompt`() {
        // The shared text IS the user's query — SHARE is interactive on purpose.
        val result = resolve(RunOrigin.SHARE)

        assertEquals(USER_PROMPT, result.text)
        assertEquals(RetrievalQuerySource.USER_PROMPT, result.source)
    }

    @Test
    fun `given chat origin with a declared query when resolving then the declaration is ignored`() {
        // The declared query exists for background runs only: an interactive
        // user's own words must never be overridden by pipeline configuration.
        val result = resolve(RunOrigin.CHAT, declaredQuery = DECLARED)

        assertEquals(USER_PROMPT, result.text)
        assertEquals(RetrievalQuerySource.USER_PROMPT, result.source)
    }

    /* ---------------- Background origins: declared query wins ---------------- */

    @Test
    fun `given trigger origin with a declared query when resolving then the declaration wins`() {
        val result = resolve(RunOrigin.TRIGGER, declaredQuery = DECLARED)

        assertEquals(DECLARED, result.text)
        assertEquals(RetrievalQuerySource.DECLARED, result.source)
    }

    @Test
    fun `given scheduler origin with a declared query when resolving then the declaration wins`() {
        val result = resolve(RunOrigin.SCHEDULER, declaredQuery = DECLARED)

        assertEquals(DECLARED, result.text)
        assertEquals(RetrievalQuerySource.DECLARED, result.source)
    }

    @Test
    fun `given quick tile origin with a declared query when resolving then the declaration wins`() {
        // The tile launches a duty pipeline under a fixed prompt, so it is
        // classified as background despite being one user tap.
        val result = resolve(RunOrigin.QUICK_TILE, declaredQuery = DECLARED)

        assertEquals(DECLARED, result.text)
        assertEquals(RetrievalQuerySource.DECLARED, result.source)
    }

    /* ---------------- Background origins: fallback chain ---------------- */

    @Test
    fun `given trigger origin without a declared query when resolving then keys off the node input`() {
        val result = resolve(RunOrigin.TRIGGER, declaredQuery = null)

        assertEquals(NODE_INPUT, result.text)
        assertEquals(RetrievalQuerySource.NODE_INPUT, result.source)
    }

    @Test
    fun `given trigger origin with a blank declared query when resolving then keys off the node input`() {
        val result = resolve(RunOrigin.TRIGGER, declaredQuery = "   \n ")

        assertEquals(NODE_INPUT, result.text)
        assertEquals(RetrievalQuerySource.NODE_INPUT, result.source)
    }

    @Test
    fun `given trigger origin with a blank node input when resolving then falls back to the user prompt`() {
        val result = resolve(RunOrigin.TRIGGER, declaredQuery = null, nodeInput = "  ")

        assertEquals(USER_PROMPT, result.text)
        assertEquals(RetrievalQuerySource.USER_PROMPT, result.source)
    }

    @Test
    fun `given trigger origin with nothing declared or produced then still returns a defined key`() {
        // Degenerate but reachable: an empty prompt on a node right behind
        // INPUT. The contract guarantees a defined result rather than a crash.
        val result = resolve(RunOrigin.TRIGGER, declaredQuery = "", nodeInput = "", userPrompt = "")

        assertEquals("", result.text)
        assertEquals(RetrievalQuerySource.USER_PROMPT, result.source)
    }

    /* ---------------- Origin classification ---------------- */

    @Test
    fun `given every run origin when classifying then interactive surfaces are exactly chat and share`() {
        // Guards the exhaustive `when`: a new origin must be classified
        // deliberately, not inherit "interactive" by omission.
        val interactive = RunOrigin.entries.filter { it.isInteractive }.toSet()

        assertEquals(setOf(RunOrigin.CHAT, RunOrigin.SHARE), interactive)
    }

    @Test
    fun `given an external automation run when resolving then it keys off the declared query`() {
        // The external entry point runs in the background with nobody at the
        // screen, so its prompt is written by another app rather than by the
        // user — the one case where treating a prompt as the user's intent
        // would be actively wrong.
        val result = resolve(RunOrigin.EXTERNAL, declaredQuery = DECLARED)

        assertEquals(DECLARED, result.text)
        assertEquals(RetrievalQuerySource.DECLARED, result.source)
    }
}
