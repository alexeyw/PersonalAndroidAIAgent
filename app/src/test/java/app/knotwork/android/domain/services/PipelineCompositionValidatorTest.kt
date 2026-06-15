package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineTargetAvailability
import app.knotwork.android.domain.models.PipelineValidationError
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PipelineCompositionValidator] — the static cross-pipeline
 * cycle / depth / dangling-reference checks. The repository is backed by an
 * in-memory map so a composition graph can be assembled per test.
 */
class PipelineCompositionValidatorTest {

    private val pipelineRepository: PipelineRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val validator = PipelineCompositionValidator(pipelineRepository, settingsRepository)

    /** Descendant pipelines resolvable through the repository. */
    private val graphs = mutableMapOf<String, PipelineGraph>()

    @Before
    fun setUp() {
        every { settingsRepository.pipelineMaxNestingDepth } returns flowOf(MAX_DEPTH)
        coEvery { pipelineRepository.getPipelineById(any()) } answers { graphs[firstArg()] }
    }

    /** Builds a graph whose PIPELINE nodes reference [targets] (one node each). */
    private fun graph(id: String, vararg targets: String): PipelineGraph = PipelineGraph(
        id = id,
        name = id,
        nodes = targets.mapIndexed { i, target ->
            NodeModel(id = "$id-n$i", type = NodeType.PIPELINE, x = 0f, y = 0f, targetPipelineId = target)
        },
    )

    /** Registers [graph] as a repository-resolvable descendant. */
    private fun register(graph: PipelineGraph) {
        graphs[graph.id] = graph
    }

    @Test
    fun `given no pipeline nodes when validate then returns no errors`() = runTest {
        val root = PipelineGraph(
            id = "root",
            name = "root",
            nodes = listOf(NodeModel("n1", NodeType.TOOL, 0f, 0f)),
        )

        assertEquals(emptyList<PipelineValidationError>(), validator.validate(root))
    }

    @Test
    fun `given a sound shallow composition when validate then returns no errors`() = runTest {
        register(graph("a")) // leaf
        val root = graph("root", "a")

        assertEquals(emptyList<PipelineValidationError>(), validator.validate(root))
    }

    @Test
    fun `given self reference when validate then reports a cycle`() = runTest {
        val root = graph("root", "root")

        val errors = validator.validate(root)

        assertEquals(
            listOf(PipelineValidationError.PipelineCycle(listOf("root", "root"))),
            errors,
        )
    }

    @Test
    fun `given a two-pipeline cycle when validate then reports the chain`() = runTest {
        register(graph("b", "root")) // b -> root closes the loop
        val root = graph("root", "b")

        val errors = validator.validate(root)

        assertEquals(
            listOf(PipelineValidationError.PipelineCycle(listOf("root", "b", "root"))),
            errors,
        )
    }

    @Test
    fun `given a three-pipeline cycle when validate then reports the chain`() = runTest {
        register(graph("b", "c"))
        register(graph("c", "root"))
        val root = graph("root", "b")

        val errors = validator.validate(root)

        assertEquals(
            listOf(PipelineValidationError.PipelineCycle(listOf("root", "b", "c", "root"))),
            errors,
        )
    }

    @Test
    fun `given a dangling reference when validate then reports target not found`() = runTest {
        val root = graph("root", "ghost") // "ghost" is never registered

        val errors = validator.validate(root)

        assertEquals(
            listOf(PipelineValidationError.TargetPipelineNotFound("root-n0", "ghost")),
            errors,
        )
    }

    @Test
    fun `given a chain exactly at the depth limit when validate then returns no errors`() = runTest {
        // root(0) -> a(1) -> b(2) -> c(3), with limit = 3.
        register(graph("a", "b"))
        register(graph("b", "c"))
        register(graph("c")) // leaf at depth 3
        val root = graph("root", "a")

        assertEquals(emptyList<PipelineValidationError>(), validator.validate(root))
    }

    @Test
    fun `given a chain past the depth limit when validate then reports too deep`() = runTest {
        // root(0) -> a(1) -> b(2) -> c(3) -> d(4), exceeding limit = 3 at d.
        register(graph("a", "b"))
        register(graph("b", "c"))
        register(graph("c", "d"))
        register(graph("d"))
        val root = graph("root", "a")

        val errors = validator.validate(root)

        assertTrue(errors.any { it is PipelineValidationError.PipelineNestingTooDeep })
        val tooDeep = errors.filterIsInstance<PipelineValidationError.PipelineNestingTooDeep>().single()
        assertEquals(listOf("root", "a", "b", "c", "d"), tooDeep.pipelineChain)
        assertEquals(MAX_DEPTH, tooDeep.limit)
    }

    // ── classifyTargets ────────────────────────────────────────────────────

    /** Registers every graph and exposes them through `getAllPipelines()`. */
    private fun setAllPipelines(vararg gs: PipelineGraph) {
        gs.forEach { register(it) }
        every { pipelineRepository.getAllPipelines() } returns flowOf(gs.toList())
    }

    @Test
    fun `given candidates when classifyTargets then marks self, cycle, depth and selectable`() = runTest {
        val root = graph("root") // the pipeline being edited (a leaf)
        val leaf = graph("leaf") // safe target
        val caller = graph("caller", "root") // already runs root -> picking it loops
        // deep -> d1 -> d2 -> d3 : subtree depth 3, so nesting under root would hit 4 > limit 3.
        val deep = graph("deep", "d1")
        val d1 = graph("d1", "d2")
        val d2 = graph("d2", "d3")
        val d3 = graph("d3")
        setAllPipelines(root, leaf, caller, deep, d1, d2, d3)

        val byId = validator.classifyTargets(editingPipelineId = "root", editingGraph = root)
            .associateBy { it.pipelineId }

        assertEquals(PipelineTargetAvailability.Reason.Self, byId.getValue("root").reason)
        assertTrue(byId.getValue("leaf").selectable)
        assertEquals(PipelineTargetAvailability.Reason.Cycle("caller"), byId.getValue("caller").reason)
        assertEquals(PipelineTargetAvailability.Reason.Depth(MAX_DEPTH), byId.getValue("deep").reason)
    }

    @Test
    fun `given an in-memory edit closing a loop when classifyTargets then the candidate is a cycle`() = runTest {
        // Persisted: root has no targets; "b" runs root. The in-memory edit adds
        // root -> (about to pick) — picking b must be rejected as a cycle even
        // though the persisted root is still a leaf.
        val persistedRoot = graph("root")
        val b = graph("b", "root")
        setAllPipelines(persistedRoot, b)

        val result = validator.classifyTargets(editingPipelineId = "root", editingGraph = persistedRoot)

        assertEquals(
            PipelineTargetAvailability.Reason.Cycle("b"),
            result.single { it.pipelineId == "b" }.reason,
        )
    }

    @Test
    fun `given saved pipelines when classifyTargets then results are sorted by name`() = runTest {
        setAllPipelines(graph("zeta"), graph("alpha"), graph("mid"))

        val names = validator.classifyTargets(editingPipelineId = "none", editingGraph = graph("none"))
            .map { it.name }

        assertEquals(listOf("alpha", "mid", "zeta"), names)
    }

    // ── findDependentPipelines ─────────────────────────────────────────────

    @Test
    fun `given pipelines referencing a target when findDependentPipelines then returns only the dependents`() {
        val a = graph("a", "x") // depends on x
        val b = graph("b", "y") // unrelated
        val c = graph("c", "x", "z") // depends on x (among others)
        val x = graph("x") // the target itself — excluded even if it self-referenced

        val deps = findDependentPipelines(targetPipelineId = "x", all = listOf(a, b, c, x))

        assertEquals(listOf("a", "c"), deps.map { it.id })
    }

    private companion object {
        const val MAX_DEPTH = 3
    }
}
