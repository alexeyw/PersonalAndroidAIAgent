package ai.agent.android.domain.promptio

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptPreset
import ai.agent.android.domain.models.PromptPresetImportOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests pinning the round-trip contract of
 * [PromptPresetJsonSerializer]. The catalogue-level validation lives in
 * [ai.agent.android.domain.promptio.PromptPresetCatalogValidationTest];
 * this test exercises edge cases with synthetic fixtures so a regression
 * in the parser surfaces in CI rather than at runtime on a real device.
 */
class PromptPresetJsonSerializerTest {

    private val samplePreset = PromptPreset(
        id = "test_concise",
        name = "Concise",
        description = "Single-paragraph answers.",
        nodeType = NodeType.LITE_RT,
        systemPrompt = "You are concise. Today is \$DATE.",
        tags = listOf("concise", "starter"),
        isBundled = false,
    )

    @Test
    fun `given preset when serialize then round-trips back as Success`() {
        val json = PromptPresetJsonSerializer.serialize(samplePreset)

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue("Expected Success but got $outcome", outcome is PromptPresetImportOutcome.Success)
        val parsed = (outcome as PromptPresetImportOutcome.Success).preset
        assertEquals(samplePreset.id, parsed.id)
        assertEquals(samplePreset.name, parsed.name)
        assertEquals(samplePreset.description, parsed.description)
        assertEquals(samplePreset.nodeType, parsed.nodeType)
        assertEquals(samplePreset.systemPrompt, parsed.systemPrompt)
        assertEquals(samplePreset.tags, parsed.tags)
        assertFalse(parsed.isBundled)
    }

    @Test
    fun `given isBundled true when parse then preset carries isBundled true`() {
        val json = PromptPresetJsonSerializer.serialize(samplePreset)

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = true)

        val parsed = (outcome as PromptPresetImportOutcome.Success).preset
        assertTrue(parsed.isBundled)
    }

    @Test
    fun `given invalid JSON when parse then returns Failure`() {
        val outcome = PromptPresetJsonSerializer.parse("{not valid json", isBundled = true)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
        assertTrue((outcome as PromptPresetImportOutcome.Failure).message.contains("Invalid JSON"))
    }

    @Test
    fun `given missing schemaVersion when parse then returns Failure`() {
        val json = JSONObject().apply {
            put("id", "x")
            put("name", "y")
            put("nodeType", "LITE_RT")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
        assertTrue((outcome as PromptPresetImportOutcome.Failure).message.contains("schemaVersion"))
    }

    @Test
    fun `given missing id when parse then returns Failure`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("name", "y")
            put("nodeType", "LITE_RT")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
    }

    @Test
    fun `given missing systemPrompt when parse then returns Failure`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", "x")
            put("name", "y")
            put("nodeType", "LITE_RT")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
        assertTrue((outcome as PromptPresetImportOutcome.Failure).message.contains("systemPrompt"))
    }

    @Test
    fun `given unknown NodeType when parse then returns Failure`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", "x")
            put("name", "y")
            put("nodeType", "MAGICAL_TYPE")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
        assertTrue((outcome as PromptPresetImportOutcome.Failure).message.contains("Unknown NodeType"))
    }

    @Test
    fun `given non-LLM NodeType when parse then returns Failure`() {
        // TOOL is a non-LLM node type — saving a prompt preset against it
        // is meaningless because its executor never feeds systemPrompt to
        // a model. The serializer must reject it at parse time.
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", "x")
            put("name", "y")
            put("nodeType", "TOOL")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        assertTrue(outcome is PromptPresetImportOutcome.Failure)
        assertTrue((outcome as PromptPresetImportOutcome.Failure).message.contains("not LLM-driven"))
    }

    @Test
    fun `given mismatched schemaVersion when parse then returns SchemaMismatch with best-effort preset`() {
        val json = JSONObject().apply {
            put("schemaVersion", 99)
            put("id", "x")
            put("name", "y")
            put("nodeType", "OUTPUT")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = true)

        assertTrue(outcome is PromptPresetImportOutcome.SchemaMismatch)
        val mismatch = outcome as PromptPresetImportOutcome.SchemaMismatch
        assertEquals(99, mismatch.foundVersion)
        assertEquals(PromptPresetJsonSerializer.CURRENT_SCHEMA_VERSION, mismatch.expectedVersion)
        assertEquals("x", mismatch.preset.id)
    }

    @Test
    fun `given missing tags array when parse then tags decode to empty list`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", "x")
            put("name", "y")
            put("nodeType", "LITE_RT")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        val parsed = (outcome as PromptPresetImportOutcome.Success).preset
        assertEquals(emptyList<String>(), parsed.tags)
    }

    @Test
    fun `given missing description when parse then description decodes to empty string`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("id", "x")
            put("name", "y")
            put("nodeType", "LITE_RT")
            put("systemPrompt", "z")
        }.toString()

        val outcome = PromptPresetJsonSerializer.parse(json, isBundled = false)

        val parsed = (outcome as PromptPresetImportOutcome.Success).preset
        assertEquals("", parsed.description)
    }
}
