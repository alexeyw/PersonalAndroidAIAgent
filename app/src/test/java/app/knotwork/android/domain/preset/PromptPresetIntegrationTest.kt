package app.knotwork.android.domain.preset

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.models.PromptPresetImportOutcome
import app.knotwork.android.domain.prompt.PromptTemplateEngine
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.promptio.PromptPresetJsonSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end integration test for the **prompt-preset** path
 * (Phase 24 / Task 9):
 *
 * ```
 * bundled JSON ──parse──▶ PromptPreset ──apply──▶ NodeModel.systemPrompt
 *              ──render via PromptTemplateEngine──▶ resolved prompt
 * ```
 *
 * Where the existing `PromptPresetCatalogValidationTest` pins the *shipped
 * artefacts* (filenames, parse success, length / NodeType / variable
 * whitelist), this test wires the real [PromptPresetJsonSerializer] to the
 * real [PromptTemplateEngine] and proves that applying a bundled preset's
 * `systemPrompt` to a node and rendering it produces a prompt with **every
 * registered `$VARIABLE` substituted** — i.e. the two halves of the feature
 * (catalogue + templating) actually compose at runtime.
 *
 * Pure JVM (no Robolectric, no `Context`): bundled files are read straight
 * from the filesystem. Gradle's `:app:test` task runs in the `app/` working
 * directory, so the relative path `src/main/assets/presets/prompts`
 * resolves correctly — the same idiom as the catalogue tests.
 */
class PromptPresetIntegrationTest {

    private val engine = PromptTemplateEngine()

    private val catalogDir: File = File("src/main/assets/presets/prompts")

    /**
     * Matches `$KEY` tokens that are NOT escaped with a backslash — the same
     * grammar [PromptTemplateEngine] recognises. Used to assert that no
     * *registered* variable token survives a render.
     */
    private val variableTokenRegex: Regex = Regex("(?<!\\\\)\\$([A-Z_][A-Z0-9_]*)")

    /**
     * Every runtime-registered prompt variable, paired with a deterministic
     * sample value. Source of truth: `di/PromptTemplateModule.kt`. The values
     * are deliberately free of `$KEY`-shaped substrings so that the
     * "no registered token survives" assertion cannot be tripped by the
     * substituted value itself.
     */
    private val sampleValues: Map<String, String> = mapOf(
        "DATE" to "29 May 2026",
        "TIME" to "14:30",
        "TOOLS" to "search — Search the web",
        "MODEL" to "Gemma 3n E2B",
        "MEMORY_SUMMARY" to "1. Prefers metric units",
        "LANG" to "en-US",
        "LOCATION" to "US",
        "USER" to "Anonymous",
        "DEVICE" to "Google Pixel 9 · Android 16",
    )

    /** All providers, ready to feed the engine. */
    private val providers: List<PromptVariableProvider> =
        sampleValues.map { (key, value) -> FakeVariableProvider(key, value) }

    @Test
    fun `applying a bundled preset to a node and rendering substitutes every variable`() = runTest {
        // Given — a representative bundled preset that references variables.
        val preset = parseBundled("litert_concise_assistant.json")
        assertTrue(
            "Fixture preset is expected to reference \$MODEL / \$DATE",
            preset.systemPrompt.contains("\$MODEL") && preset.systemPrompt.contains("\$DATE"),
        )

        // When — the preset body is applied to a matching node and rendered.
        val node = NodeModel(
            id = "lite_rt",
            type = NodeType.LITE_RT,
            x = 0f,
            y = 0f,
            systemPrompt = preset.systemPrompt,
        )
        val rendered = engine.render(node.systemPrompt!!, providers)

        // Then — the concrete sample values appear and no registered token remains.
        assertTrue(
            "Rendered prompt should contain the substituted \$MODEL value",
            rendered.contains(sampleValues.getValue("MODEL")),
        )
        assertTrue(
            "Rendered prompt should contain the substituted \$DATE value",
            rendered.contains(sampleValues.getValue("DATE")),
        )
        assertNoRegisteredTokenRemains("litert_concise_assistant.json", rendered)
    }

    @Test
    fun `every bundled preset renders with all registered variables substituted`() = runTest {
        forEachBundledFile { file ->
            val preset = parseBundled(file.name)
            val rendered = engine.render(preset.systemPrompt, providers)
            assertNoRegisteredTokenRemains(file.name, rendered)
        }
    }

    @Test
    fun `render keeps unknown tokens verbatim and unescapes literal dollar keys`() = runTest {
        // $UNREGISTERED is not a provider → kept verbatim; \$KEEP → literal $KEEP;
        // $MODEL → resolved. This guards the contract a preset author relies on.
        val template = "Literal \\\$KEEP, unknown \$UNREGISTERED, model \$MODEL."

        val rendered = engine.render(template, providers)

        assertEquals(
            "Literal \$KEEP, unknown \$UNREGISTERED, model ${sampleValues.getValue("MODEL")}.",
            rendered,
        )
    }

    /**
     * Asserts that no token whose key is a *registered* variable survives the
     * render. Unknown tokens (typos) are intentionally ignored here — the
     * catalogue test already forbids them in shipped presets.
     */
    private fun assertNoRegisteredTokenRemains(fileName: String, rendered: String) {
        val survivingRegistered = variableTokenRegex.findAll(rendered)
            .map { it.groupValues[1] }
            .filter { it in sampleValues.keys }
            .toSet()
        assertFalse(
            "Rendered prompt for $fileName still contains unsubstituted registered " +
                "variable(s): $survivingRegistered",
            survivingRegistered.isNotEmpty(),
        )
    }

    private fun parseBundled(fileName: String): PromptPreset {
        val outcome = PromptPresetJsonSerializer.parse(
            File(catalogDir, fileName).readText(),
            isBundled = true,
        )
        assertTrue(
            "Bundled preset $fileName did not parse cleanly: $outcome",
            outcome is PromptPresetImportOutcome.Success,
        )
        return (outcome as PromptPresetImportOutcome.Success).preset
    }

    private inline fun forEachBundledFile(block: (File) -> Unit) {
        val files = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue(
            "No bundled prompt presets found in ${catalogDir.absolutePath}",
            files.isNotEmpty(),
        )
        files.forEach(block)
    }

    /**
     * Minimal [PromptVariableProvider] returning a fixed value — stands in for
     * the production providers so the render is deterministic.
     */
    private class FakeVariableProvider(private val key: String, private val value: String) : PromptVariableProvider {
        override fun key(): String = key
        override suspend fun resolve(): String = value
    }
}
