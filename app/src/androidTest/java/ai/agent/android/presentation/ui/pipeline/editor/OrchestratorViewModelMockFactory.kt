@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockOrchestratorViewModel` factory function (primary
// export), its sibling `OrchestratorMockHandles` data class, and the
// `PipelineEditorTestFixtures` constructors used across every editor
// androidTest. Naming after the factory is preferred since tests reach for
// it by name.

package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.presentation.ui.orchestrator.OrchestratorUiState
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.PipelineRunState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of every [MutableStateFlow] / [MutableSharedFlow] that
 * backs [OrchestratorViewModel] from the pipeline-editor's perspective.
 * Tests mutate the flows directly to drive the screen through state
 * transitions, then call `composeTestRule.waitForIdle()` to recompose.
 *
 * Exposing the mutable handles (rather than just the readable mock) keeps
 * the tests free of MockK re-stubbing between phases of a single scenario
 * (e.g. idle → running → idle, or fresh graph → graph with validation
 * errors).
 */
internal class OrchestratorMockHandles(
    val uiStateFlow: MutableStateFlow<OrchestratorUiState>,
    val runStateFlow: MutableStateFlow<PipelineRunState>,
    val focusNodeRequestFlow: MutableSharedFlow<String>,
)

/**
 * Builds a relaxed [OrchestratorViewModel] mock with every observed flow
 * stubbed to a deterministic starting value plus a sibling
 * [OrchestratorMockHandles] bundle that lets a test mutate any flow
 * without re-stubbing.
 *
 * `addNode` is stubbed to return a freshly-generated id each call so
 * production callsites (`PipelineEditorScreen.onAddNode`) can stash the
 * new id into `editor.configuringNodeId` without exploding on a null
 * return.
 *
 * `labelFor(...)` is stubbed to delegate to the production
 * `OrchestratorUiState.validationErrors → label` so `ValidationBar`
 * tests render real copy without manual override.
 */
@Suppress("LongParameterList")
internal fun mockOrchestratorViewModel(
    initialUiState: OrchestratorUiState = OrchestratorUiState(),
    initialRunState: PipelineRunState = PipelineRunState(),
): Pair<OrchestratorViewModel, OrchestratorMockHandles> {
    val uiStateFlow = MutableStateFlow(initialUiState)
    val runStateFlow = MutableStateFlow(initialRunState)
    val focusNodeRequestFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val vm = mockk<OrchestratorViewModel>(relaxed = true)
    every { vm.uiState } returns uiStateFlow
    every { vm.runState } returns runStateFlow
    every { vm.focusNodeRequest } returns focusNodeRequestFlow
    // `addNode` returns the id of the new node — the production screen
    // assigns this id straight into `editor.configuringNodeId`, so a
    // null from a relaxed stub would crash the next composition.
    every { vm.addNode(any(), any(), any()) } answers {
        "test-node-${java.util.UUID.randomUUID().shortHex()}"
    }

    val handles = OrchestratorMockHandles(
        uiStateFlow = uiStateFlow,
        runStateFlow = runStateFlow,
        focusNodeRequestFlow = focusNodeRequestFlow,
    )
    return vm to handles
}

private fun java.util.UUID.shortHex(): String = toString().take(8)

/**
 * Hand-rolled pipeline graphs used as inputs to the editor androidTests.
 * Each helper keeps the graph small enough that asserting on rendered
 * node count is unambiguous, while still producing the topology the test
 * actually needs (valid vs. validation-invalid, multi-node, etc.).
 */
internal object PipelineEditorTestFixtures {

    /** Empty pipeline (no nodes, no connections) — drives the `EmptyPipelineState` hero. */
    fun emptyPipeline(name: String = "Empty pipeline"): PipelineGraph = PipelineGraph(
        id = "pipeline-empty",
        name = name,
    )

    /**
     * Minimal valid pipeline: a single `INPUT` wired to a single `OUTPUT`.
     * Passes `PipelineGraph.validate()` so editor tests that don't care
     * about validation errors can start from a clean state.
     */
    fun inputOutputPipeline(name: String = "IO pipeline"): PipelineGraph {
        val input = NodeModel(
            id = "node-input",
            type = NodeType.INPUT,
            x = 0f,
            y = 0f,
            label = "Input",
        )
        val output = NodeModel(
            id = "node-output",
            type = NodeType.OUTPUT,
            x = 240f,
            y = 0f,
            label = "Output",
        )
        return PipelineGraph(
            id = "pipeline-io",
            name = name,
            nodes = listOf(input, output),
            connections = listOf(
                ConnectionModel(
                    id = "conn-1",
                    sourceNodeId = input.id,
                    targetNodeId = output.id,
                ),
            ),
        )
    }

    /**
     * Three-node valid pipeline: `INPUT → LITE_RT → OUTPUT`. Useful for
     * tests that need at least one LLM-bearing node alongside the start /
     * end markers (search-by-name, mini-map, run-state telemetry).
     */
    fun threeNodePipeline(name: String = "Three-node pipeline"): PipelineGraph {
        val input = NodeModel(
            id = "node-input",
            type = NodeType.INPUT,
            x = 0f,
            y = 0f,
            label = "Input",
        )
        val lite = NodeModel(
            id = "node-lite",
            type = NodeType.LITE_RT,
            x = 240f,
            y = 0f,
            label = "Local LLM",
        )
        val output = NodeModel(
            id = "node-output",
            type = NodeType.OUTPUT,
            x = 480f,
            y = 0f,
            label = "Output",
        )
        return PipelineGraph(
            id = "pipeline-3",
            name = name,
            nodes = listOf(input, lite, output),
            connections = listOf(
                ConnectionModel(
                    id = "conn-1",
                    sourceNodeId = input.id,
                    targetNodeId = lite.id,
                ),
                ConnectionModel(
                    id = "conn-2",
                    sourceNodeId = lite.id,
                    targetNodeId = output.id,
                ),
            ),
        )
    }

    /**
     * Validation-invalid pipeline: two `INPUT` nodes with no `OUTPUT` —
     * triggers `MultipleInputs` + `MissingOutput` (both auto-fixable),
     * giving `ValidationBar` tests a non-empty error list and the
     * Auto-fix CTA something to do.
     */
    fun validationInvalidPipeline(name: String = "Invalid pipeline"): PipelineGraph {
        val inputA = NodeModel(
            id = "node-input-a",
            type = NodeType.INPUT,
            x = 0f,
            y = 0f,
            label = "Input A",
        )
        val inputB = NodeModel(
            id = "node-input-b",
            type = NodeType.INPUT,
            x = 240f,
            y = 0f,
            label = "Input B",
        )
        return PipelineGraph(
            id = "pipeline-invalid",
            name = name,
            nodes = listOf(inputA, inputB),
        )
    }

    /**
     * A small palette of `AgentTool`s for tests that need the
     * `OrchestratorUiState.availableTools` slot non-empty (e.g. the
     * `TOOL`-node config sheet).
     */
    fun sampleAvailableTools(): List<AgentTool> = listOf(
        AgentTool(
            name = "search_tool",
            description = "Search the web",
            parameters = "{}",
            risk = ToolRisk.READ_ONLY,
        ),
        AgentTool(
            name = "delegate_task",
            description = "Delegate a sub-task",
            parameters = "{}",
            risk = ToolRisk.READ_ONLY,
        ),
    )

    /**
     * A single available local model — covers the `LITE_RT`-node config
     * sheet's model picker without dragging in repository plumbing.
     */
    fun sampleAvailableLocalModels(): List<LocalModel> = listOf(
        LocalModel(
            id = 1L,
            name = "Gemma 2B IT",
            path = "/data/models/gemma-2b-it.tflite",
            size = 1_500_000_000L,
            isActive = true,
        ),
    )

    /** A `NodeContextConfig` with every flag enabled — convenience for sheet tests. */
    fun allContextEnabled(): NodeContextConfig = NodeContextConfig.ALL_ENABLED
}
