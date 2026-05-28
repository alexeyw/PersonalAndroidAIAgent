package ai.agent.android.domain.promptio

import ai.agent.android.domain.constants.PromptPresetConstants
import ai.agent.android.domain.models.PromptPresetImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Catalogue-level validation for the bundled prompt-preset JSON files that
 * ship under `app/src/main/assets/presets/prompts/`. Pure JVM (no
 * Robolectric, no `Context`): each file is read directly from the
 * filesystem and fed through [PromptPresetJsonSerializer.parse].
 *
 * Why a catalogue-level test exists separately from
 * [PromptPresetJsonSerializerTest]: the serializer test pins the
 * round-trip contract using synthetic fixtures, while this test pins the
 * *shipped* artefacts — the curated presets the user sees the first time
 * they open the Prompt Library. A broken preset would otherwise only
 * surface at runtime on a real device.
 *
 * The assertions per file are intentionally narrow:
 * - the filename set is exactly what this task promised (catches typos /
 *   accidental deletion);
 * - every file parses to [PromptPresetImportOutcome.Success] with
 *   `isBundled == true` and `id == filename stem`;
 * - every `nodeType` is in [PromptPresetConstants.LLM_DRIVEN_NODE_TYPES];
 * - every `systemPrompt` fits within
 *   [PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH];
 * - every `$VARIABLE` token used in any `systemPrompt` is one of the
 *   registered runtime providers from `di/PromptTemplateModule.kt`.
 *
 * Gradle's `:app:test` task runs in the `app/` working directory, so the
 * relative path `src/main/assets/presets/prompts` resolves correctly.
 */
class PromptPresetCatalogValidationTest {

    /**
     * Expected bundled preset filenames. Adding a new bundled preset means
     * updating this set in the same PR — keeping the gate intentionally
     * tight prevents accidental deletions sliding into a release.
     */
    private val expectedFileNames: Set<String> = setOf(
        "litert_concise_assistant.json",
        "litert_step_by_step_reasoner.json",
        "litert_memory_aware_reply.json",
        "cloud_capable_analyst.json",
        "cloud_web_savvy_researcher.json",
        "output_plain_conversational.json",
        "output_markdown_with_sections.json",
        "output_json_structured.json",
        "summary_bullet_recap.json",
        "summary_detailed_narrative.json",
        "router_keyword_classifier.json",
        "router_tool_first.json",
        "decomposition_json_subtasks.json",
        "decomposition_dependency_aware.json",
        "evaluation_pass_fail_judge.json",
        "evaluation_scored_critic.json",
        "clarification_multiple_choice.json",
        "clarification_open_ended.json",
    )

    /**
     * Whitelist of `$VARIABLE` keys that may appear in a bundled preset
     * systemPrompt. Source of truth: `di/PromptTemplateModule.kt`. When a
     * new [ai.agent.android.domain.prompt.PromptVariableProvider] is
     * registered, add its key here in the same commit.
     */
    private val knownVariableKeys: Set<String> = setOf(
        "DATE",
        "TIME",
        "TOOLS",
        "MODEL",
        "MEMORY_SUMMARY",
        "LANG",
        "LOCATION",
        "USER",
        "DEVICE",
    )

    private val catalogDir: File = File("src/main/assets/presets/prompts")

    private val variableTokenRegex: Regex = Regex("(?<!\\\\)\\$([A-Z_][A-Z0-9_]*)")

    @Test
    fun `catalog directory contains exactly the expected bundled presets`() {
        assertTrue(
            "Bundled preset directory missing: ${catalogDir.absolutePath}",
            catalogDir.isDirectory,
        )
        val actual = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        assertEquals(expectedFileNames, actual)
    }

    @Test
    fun `every bundled preset parses as Success`() {
        forEachBundledFile { file ->
            val outcome = PromptPresetJsonSerializer.parse(file.readText(), isBundled = true)
            assertTrue(
                "Bundled preset ${file.name} did not parse cleanly: $outcome",
                outcome is PromptPresetImportOutcome.Success,
            )
            val success = outcome as PromptPresetImportOutcome.Success
            assertTrue(
                "Bundled preset ${file.name} must be marked isBundled = true",
                success.preset.isBundled,
            )
            assertEquals(
                "Preset id should equal the filename stem for ${file.name}",
                file.nameWithoutExtension,
                success.preset.id,
            )
        }
    }

    @Test
    fun `every bundled preset targets an LLM-driven NodeType`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            assertTrue(
                "Bundled preset ${file.name} targets ${preset.nodeType} which is not LLM-driven",
                preset.nodeType in PromptPresetConstants.LLM_DRIVEN_NODE_TYPES,
            )
        }
    }

    @Test
    fun `every bundled systemPrompt fits within MAX_SYSTEM_PROMPT_LENGTH`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            assertTrue(
                "Bundled preset ${file.name} systemPrompt length ${preset.systemPrompt.length} " +
                    "exceeds MAX_SYSTEM_PROMPT_LENGTH (${PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH})",
                preset.systemPrompt.length <= PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH,
            )
        }
    }

    @Test
    fun `every bundled name fits within MAX_NAME_LENGTH`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            assertTrue(
                "Bundled preset ${file.name} name length ${preset.name.length} " +
                    "exceeds MAX_NAME_LENGTH (${PromptPresetConstants.MAX_NAME_LENGTH})",
                preset.name.length <= PromptPresetConstants.MAX_NAME_LENGTH,
            )
        }
    }

    @Test
    fun `every system prompt uses only registered VARIABLE tokens`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            val usedKeys = variableTokenRegex.findAll(preset.systemPrompt)
                .map { it.groupValues[1] }
                .toSet()
            val unknown = usedKeys - knownVariableKeys
            assertTrue(
                "Bundled preset ${file.name} references unknown prompt variable(s) $unknown — " +
                    "register them in di/PromptTemplateModule.kt or fix the typo.",
                unknown.isEmpty(),
            )
        }
    }

    @Test
    fun `every LLM-driven NodeType has at least one bundled preset`() {
        val byType = expectedFileNames
            .map { File(catalogDir, it) }
            .map { parseAsSuccess(it).preset.nodeType }
            .toSet()
        val missing = PromptPresetConstants.LLM_DRIVEN_NODE_TYPES - byType
        assertTrue(
            "Bundled catalogue is missing presets for: $missing — every LLM-driven NodeType " +
                "must have at least one bundled prompt to surface in the Prompt Library.",
            missing.isEmpty(),
        )
    }

    private fun parseAsSuccess(file: File): PromptPresetImportOutcome.Success {
        val outcome = PromptPresetJsonSerializer.parse(file.readText(), isBundled = true)
        assertNotNull("Parse outcome was null for ${file.name}", outcome)
        assertTrue(
            "Expected Success for ${file.name} but got $outcome",
            outcome is PromptPresetImportOutcome.Success,
        )
        return outcome as PromptPresetImportOutcome.Success
    }

    private inline fun forEachBundledFile(block: (File) -> Unit) {
        val files = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue(
            "No bundled preset files found in ${catalogDir.absolutePath}",
            files.isNotEmpty(),
        )
        files.forEach(block)
    }
}
