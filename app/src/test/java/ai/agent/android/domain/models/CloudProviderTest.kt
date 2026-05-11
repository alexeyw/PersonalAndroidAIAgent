package ai.agent.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [CloudProvider] — the canonical typed identifier for cloud LLM providers.
 *
 * Covers id round-trip, case-insensitivity, the historical `"gemini"` alias for
 * [CloudProvider.GOOGLE], and the safe-failure contract for unknown / blank / `null` ids
 * promised by [CloudProvider.fromId].
 */
class CloudProviderTest {

    @Test
    fun `given each enum value when its id is parsed then the same value is returned`() {
        // Round-trip guard: any future provider added to the enum must keep the
        // value ↔ id bijection so persisted pipeline JSON keeps loading correctly.
        CloudProvider.entries.forEach { provider ->
            assertEquals(provider, CloudProvider.fromId(provider.id))
        }
    }

    @Test
    fun `given uppercase id when fromId is called then matching is case-insensitive`() {
        assertEquals(CloudProvider.OPENAI, CloudProvider.fromId("OPENAI"))
        assertEquals(CloudProvider.ANTHROPIC, CloudProvider.fromId("Anthropic"))
    }

    @Test
    fun `given legacy 'gemini' alias when fromId is called then GOOGLE is returned`() {
        // Earlier builds persisted "gemini" for Google models. The alias keeps those
        // pipelines loading without a manual migration.
        assertEquals(CloudProvider.GOOGLE, CloudProvider.fromId("gemini"))
    }

    @Test
    fun `given AUTO_KEY when fromId is called then null is returned`() {
        // AUTO_KEY is a UI sentinel, not a real provider; callers must handle it before
        // dispatching, which is enforced by fromId rejecting it.
        assertNull(CloudProvider.fromId(CloudProvider.AUTO_KEY))
    }

    @Test
    fun `given unknown id when fromId is called then null is returned`() {
        assertNull(CloudProvider.fromId("does-not-exist"))
    }

    @Test
    fun `given null when fromId is called then null is returned`() {
        assertNull(CloudProvider.fromId(null))
    }

    @Test
    fun `given blank string when fromId is called then null is returned`() {
        assertNull(CloudProvider.fromId(""))
    }
}
