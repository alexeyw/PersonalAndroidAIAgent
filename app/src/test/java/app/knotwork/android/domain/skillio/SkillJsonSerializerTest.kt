package app.knotwork.android.domain.skillio

import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.Skill
import app.knotwork.android.domain.models.SkillImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip and edge-case contract for [SkillJsonSerializer]. The
 * load-bearing property under test is that the `toolAllowlist`
 * null-vs-empty distinction survives serialize → parse exactly.
 */
class SkillJsonSerializerTest {

    private val sample = Skill(
        id = "summarizer",
        name = "Summarizer",
        description = "Condenses input.",
        instruction = "Summarise the text. Today is \$DATE.",
        toolAllowlist = listOf("write_file"),
        contextConfig = NodeContextConfig(
            chatHistory = false,
            originalTask = true,
            nodeInput = true,
            longTermMemory = false,
            toolResults = true,
        ),
        isBundled = true,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    @Test
    fun `given a subset allowlist when round-tripped then it is preserved`() {
        val json = SkillJsonSerializer.serialize(sample)
        val outcome = SkillJsonSerializer.parse(json, isBundled = true)
        val skill = (outcome as SkillImportOutcome.Success).skill
        assertEquals(sample.id, skill.id)
        assertEquals(sample.name, skill.name)
        assertEquals(sample.instruction, skill.instruction)
        assertEquals(listOf("write_file"), skill.toolAllowlist)
        assertEquals(sample.contextConfig, skill.contextConfig)
        assertEquals(1_000L, skill.createdAt)
        assertEquals(2_000L, skill.updatedAt)
        assertTrue(skill.isBundled)
    }

    @Test
    fun `given null allowlist (all tools) when round-tripped then it stays null`() {
        val allTools = sample.copy(toolAllowlist = null)
        val outcome = SkillJsonSerializer.parse(SkillJsonSerializer.serialize(allTools), isBundled = false)
        assertNull((outcome as SkillImportOutcome.Success).skill.toolAllowlist)
    }

    @Test
    fun `given empty allowlist (no tools) when round-tripped then it stays empty not null`() {
        val noTools = sample.copy(toolAllowlist = emptyList())
        val outcome = SkillJsonSerializer.parse(SkillJsonSerializer.serialize(noTools), isBundled = false)
        assertEquals(emptyList<String>(), (outcome as SkillImportOutcome.Success).skill.toolAllowlist)
    }

    @Test
    fun `given missing toolAllowlist key when parsed then it defaults to null (all tools)`() {
        val json = """
            {"schemaVersion":1,"id":"x","name":"X","instruction":"do it"}
        """.trimIndent()
        val outcome = SkillJsonSerializer.parse(json, isBundled = true)
        assertNull((outcome as SkillImportOutcome.Success).skill.toolAllowlist)
    }

    @Test
    fun `given missing contextConfig when parsed then defaults to ALL_ENABLED`() {
        val json = """
            {"schemaVersion":1,"id":"x","name":"X","instruction":"do it"}
        """.trimIndent()
        val outcome = SkillJsonSerializer.parse(json, isBundled = true)
        assertEquals(NodeContextConfig.ALL_ENABLED, (outcome as SkillImportOutcome.Success).skill.contextConfig)
    }

    @Test
    fun `given a higher schemaVersion when parsed then it reports a schema mismatch`() {
        val json = """
            {"schemaVersion":99,"id":"x","name":"X","instruction":"do it"}
        """.trimIndent()
        val outcome = SkillJsonSerializer.parse(json, isBundled = true)
        assertTrue(outcome is SkillImportOutcome.SchemaMismatch)
        assertEquals(99, (outcome as SkillImportOutcome.SchemaMismatch).foundVersion)
    }

    @Test
    fun `given malformed json when parsed then it fails gracefully`() {
        val outcome = SkillJsonSerializer.parse("{ not json", isBundled = true)
        assertTrue(outcome is SkillImportOutcome.Failure)
    }

    @Test
    fun `given missing required fields when parsed then it fails`() {
        assertTrue(SkillJsonSerializer.parse("""{"schemaVersion":1}""", isBundled = true) is SkillImportOutcome.Failure)
        assertTrue(
            SkillJsonSerializer.parse("""{"schemaVersion":1,"id":"x","name":"X"}""", isBundled = true)
                is SkillImportOutcome.Failure,
        )
        assertTrue(
            SkillJsonSerializer.parse("""{"id":"x","name":"X","instruction":"i"}""", isBundled = true)
                is SkillImportOutcome.Failure,
        )
    }
}
