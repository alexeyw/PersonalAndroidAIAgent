package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineBundleImportOutcome
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the pipeline recipes published in `docs/recipes/`, which
 * [docs/cookbook.md] tells a reader to download and import.
 *
 * A recipe that does not import is worse than no recipe: the reader follows
 * the documented path, the app reports a parse failure or "N settings could
 * not be read", and the conclusion they draw is about the app rather than
 * about the file. So these files go through the **same parser the Import JSON
 * button uses** — not a validator written alongside them — and the graphs they
 * produce through the same [PipelineGraph.validate] the editor's validation bar
 * runs.
 *
 * What each recipe is asserted to satisfy:
 *
 * - the published set is exactly [expectedFileNames], so a deleted or renamed
 *   recipe fails here rather than silently becoming a dead link;
 * - every recipe is linked from `docs/cookbook.md`, so one cannot rot unread;
 * - the four single-pipeline documents parse to
 *   [PipelineImportOutcome.Success] with **no dropped fields** — a dropped key
 *   is the exact failure the format's own forward-compatibility rule makes
 *   silent, and a recipe is the last place it should happen;
 * - the bundle parses to [PipelineBundleImportOutcome.Success], which also
 *   settles its referential integrity;
 * - every graph validates with zero errors;
 * - every `$VARIABLE` used in a prompt is one the app actually registers —
 *   read out of `di/PromptTemplateModule.kt` and the provider sources rather
 *   than from a list kept here, so registering a tenth provider does not
 *   require editing this test and removing one is caught;
 * - every `INTENT_ROUTER`'s declared classes match its outgoing edge labels,
 *   because routing happens on the edges while the classes only draw the
 *   ports: a document where the two disagree looks right in the editor and
 *   routes wrongly at run time.
 *
 * The last test is the negative control: it breaks a parsed recipe in memory
 * and asserts the validator notices, so a future commit that widens
 * `validate()` cannot leave this gate green and empty.
 *
 * Gradle's `:app:test` runs in the `app/` working directory, so the recipes
 * resolve as `../docs/recipes`.
 */
class CookbookRecipeValidationTest {

    /**
     * The published recipe files. Adding a recipe means adding it here in the
     * same change — a tight set is what turns "the folder is fine" into "these
     * five documents are fine".
     */
    private val expectedFileNames: Set<String> = setOf(
        "intent-routing.json",
        "decompose-and-queue.json",
        "tool-with-approval.json",
        "memory-aware-run.json",
        "composition-bundle.json",
    )

    /** The one recipe shipped as a bundle envelope rather than a single pipeline. */
    private val bundleFileName = "composition-bundle.json"

    private val recipeDir = File("../docs/recipes")
    private val cookbook = File("../docs/cookbook.md")
    private val promptModule = File("src/main/java/app/knotwork/android/di/PromptTemplateModule.kt")
    private val promptProviderDir = File("src/main/java/app/knotwork/android/data/prompt")

    @Test
    fun `given the recipes folder when listed then it holds exactly the published set`() {
        val actual = recipeDir.listFiles { file -> file.extension == "json" }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()

        assertEquals(expectedFileNames, actual)
    }

    @Test
    fun `given a published recipe when the cookbook is read then the recipe is linked from it`() {
        val markdown = cookbook.readText()

        expectedFileNames.forEach { name ->
            assertTrue(
                "docs/cookbook.md does not link recipes/$name — an unlinked recipe cannot be found.",
                markdown.contains("recipes/$name"),
            )
        }
    }

    @Test
    fun `given a single-pipeline recipe when parsed then it imports with nothing dropped`() {
        singlePipelineFiles().forEach { file ->
            val outcome = PipelineJsonSerializer.parse(file.readText())

            assertTrue(
                "${file.name} did not parse to Success but to $outcome.",
                outcome is PipelineImportOutcome.Success,
            )
            val success = outcome as PipelineImportOutcome.Success
            assertEquals(
                "${file.name} carries keys this build cannot read: ${success.droppedFields}.",
                emptyList<String>(),
                success.droppedFields,
            )
        }
    }

    @Test
    fun `given the bundle recipe when parsed then every sub-pipeline reference resolves`() {
        val outcome = PipelineBundleJsonSerializer.parse(File(recipeDir, bundleFileName).readText())

        assertTrue(
            "$bundleFileName did not parse to Success but to $outcome.",
            outcome is PipelineBundleImportOutcome.Success,
        )
        val pipelines = (outcome as PipelineBundleImportOutcome.Success).pipelines
        val ids = pipelines.map { it.id }.toSet()
        pipelines.flatMap { it.nodes }
            .filter { it.type == NodeType.PIPELINE }
            .forEach { node ->
                assertTrue(
                    "PIPELINE node `${node.id}` targets `${node.targetPipelineId}`, absent from the bundle.",
                    node.targetPipelineId in ids,
                )
            }
    }

    @Test
    fun `given a recipe graph when validated then it reports no errors`() {
        allGraphs().forEach { (name, graph) ->
            assertEquals("$name failed graph validation.", emptyList<Any>(), graph.validate())
        }
    }

    @Test
    fun `given a recipe prompt when its variables are read then each one is registered`() {
        val registered = registeredVariableKeys()
        assertTrue("Parsed no registered prompt variables — the parse is wrong, not the recipes.", registered.size >= 2)

        allGraphs().forEach { (name, graph) ->
            graph.nodes.forEach { node ->
                VARIABLE_RE.findAll(node.systemPrompt.orEmpty()).forEach { match ->
                    val key = match.groupValues[1]
                    assertTrue(
                        "$name node `${node.id}` uses \$$key, which no registered provider resolves.",
                        key in registered,
                    )
                }
            }
        }
    }

    @Test
    fun `given a router recipe when its classes are read then they match its outgoing edges`() {
        allGraphs().forEach { (name, graph) ->
            graph.nodes.filter { it.type == NodeType.INTENT_ROUTER }.forEach { router ->
                val declared = ROUTER_CLASS_RE.findAll(router.configJson.orEmpty())
                    .map { it.groupValues[1] }
                    .toSet()
                val labelled = graph.connections
                    .filter { it.sourceNodeId == router.id }
                    .mapNotNull { it.label }
                    .toSet()

                assertTrue("$name router `${router.id}` declares no classes.", declared.isNotEmpty())
                assertEquals(
                    "$name router `${router.id}`: declared classes and outgoing edge labels disagree.",
                    declared,
                    labelled,
                )
            }
        }
    }

    @Test
    fun `given a broken recipe graph when validated then the validator reports it`() {
        val (name, graph) = allGraphs().first()
        val withoutOutput = graph.copy(nodes = graph.nodes.filterNot { it.type == NodeType.OUTPUT })

        assertTrue(
            "Removing the OUTPUT node from $name produced no validation error — the gate is not testing anything.",
            withoutOutput.validate().isNotEmpty(),
        )
    }

    /** The recipes that are single pipelines rather than the bundle envelope. */
    private fun singlePipelineFiles(): List<File> = expectedFileNames
        .filterNot { it == bundleFileName }
        .sorted()
        .map { File(recipeDir, it) }

    /** Every graph published as a recipe, paired with the file it came from. */
    private fun allGraphs(): List<Pair<String, PipelineGraph>> {
        val singles = singlePipelineFiles().map { file ->
            val outcome = PipelineJsonSerializer.parse(file.readText())
            file.name to (outcome as PipelineImportOutcome.Success).graph
        }
        val bundle = PipelineBundleJsonSerializer.parse(File(recipeDir, bundleFileName).readText())
        val bundled = (bundle as PipelineBundleImportOutcome.Success).pipelines
            .map { "$bundleFileName (${it.name})" to it }
        return singles + bundled
    }

    /**
     * The `$VARIABLE` keys the app actually resolves at run time: the providers
     * bound into the Hilt set, each resolved to its own `KEY` constant.
     *
     * Derived rather than listed so the test cannot drift from the registration
     * — a provider file that exists but is not bound is correctly absent, and a
     * newly bound one needs no edit here.
     */
    private fun registeredVariableKeys(): Set<String> {
        val module = promptModule.readText()
        return BOUND_PROVIDER_RE.findAll(module).mapNotNull { match ->
            val providerClass = match.groupValues[1]
            val source = File(promptProviderDir, "$providerClass.kt").takeIf { it.isFile }?.readText()
            source?.let { KEY_CONSTANT_RE.find(it)?.groupValues?.get(1) }
        }.toSet()
    }

    private companion object {
        val VARIABLE_RE = Regex("""\$([A-Z][A-Z0-9_]*)""")
        val ROUTER_CLASS_RE = Regex(""""name"\s*:\s*"([^"]+)"""")
        val BOUND_PROVIDER_RE = Regex("""impl:\s*(\w+VariableProvider)\)""")
        val KEY_CONSTANT_RE = Regex("""const val KEY\s*(?::\s*String\s*)?=\s*"([A-Z][A-Z0-9_]*)"""")
    }
}
