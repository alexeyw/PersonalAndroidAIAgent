package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PipelineValidationException
import ai.agent.android.domain.models.PresetCategory
import ai.agent.android.domain.repositories.PipelinePresetRepository
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadPipelineFromPresetUseCaseTest {

    private val presetRepository: PipelinePresetRepository = mockk(relaxed = true)
    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = LoadPipelineFromPresetUseCase(presetRepository, pipelineRepository)

    private fun validGraph(): PipelineGraph = PipelineGraph(
        id = "template-id",
        name = "Template",
        nodes = listOf(
            NodeModel(
                id = "tpl-input",
                type = NodeType.INPUT,
                x = 0f,
                y = 0f,
                contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
            ),
            NodeModel(
                id = "tpl-output",
                type = NodeType.OUTPUT,
                x = 100f,
                y = 0f,
                contextConfig = NodeContextConfig.defaultForType(NodeType.OUTPUT),
            ),
        ),
        connections = listOf(
            ConnectionModel(id = "tpl-conn", sourceNodeId = "tpl-input", targetNodeId = "tpl-output", label = "edge"),
        ),
    )

    private fun preset(graph: PipelineGraph = validGraph(), name: String = "Local-only Q&A") = PipelinePreset(
        id = "local_only_qa",
        name = name,
        description = "desc",
        category = PresetCategory.LOCAL,
        graph = graph,
        tags = listOf("offline"),
        isBundled = true,
    )

    @Test
    fun `given missing preset when invoke then returns failure without touching pipeline repository`() = runTest {
        coEvery { presetRepository.getPresetById("nope") } returns null

        val result = useCase("nope")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given valid preset when invoke then persists pipeline with fresh ids and returns its id`() = runTest {
        val source = preset()
        coEvery { presetRepository.getPresetById("local_only_qa") } returns source
        val saved = slot<PipelineGraph>()
        coEvery { pipelineRepository.savePipeline(capture(saved)) } returns Unit

        val result = useCase("local_only_qa")

        assertTrue(result.isSuccess)
        val newId = result.getOrNull()
        assertNotNull(newId)
        assertEquals(newId, saved.captured.id)
        // Pipeline id is regenerated.
        assertNotEquals("template-id", saved.captured.id)
        // Every node id is regenerated.
        assertTrue(saved.captured.nodes.none { it.id == "tpl-input" || it.id == "tpl-output" })
        // Connection ids are regenerated; source/target are remapped; label preserved.
        val connection = saved.captured.connections.single()
        assertNotEquals("tpl-conn", connection.id)
        val newInput = saved.captured.nodes.first { it.type == NodeType.INPUT }
        val newOutput = saved.captured.nodes.first { it.type == NodeType.OUTPUT }
        assertEquals(newInput.id, connection.sourceNodeId)
        assertEquals(newOutput.id, connection.targetNodeId)
        assertEquals("edge", connection.label)
    }

    @Test
    fun `given preset with orphan connections when invoke then drops orphans and keeps the rest`() = runTest {
        val orphanedGraph = validGraph().copy(
            connections = listOf(
                ConnectionModel(id = "tpl-conn", sourceNodeId = "tpl-input", targetNodeId = "tpl-output"),
                ConnectionModel(id = "ghost-1", sourceNodeId = "tpl-input", targetNodeId = "ghost"),
                ConnectionModel(id = "ghost-2", sourceNodeId = "ghost", targetNodeId = "tpl-output"),
            ),
        )
        coEvery { presetRepository.getPresetById("p") } returns preset(graph = orphanedGraph)
        val saved = slot<PipelineGraph>()
        coEvery { pipelineRepository.savePipeline(capture(saved)) } returns Unit

        val result = useCase("p")

        assertTrue(result.isSuccess)
        assertEquals(1, saved.captured.connections.size)
    }

    @Test
    fun `given template that fails validation when invoke then returns PipelineValidationException`() = runTest {
        // INPUT-only graph: missing OUTPUT triggers a validation error.
        val brokenGraph = PipelineGraph(
            id = "broken",
            name = "Broken",
            nodes = listOf(
                NodeModel(id = "i", type = NodeType.INPUT, x = 0f, y = 0f),
            ),
        )
        coEvery { presetRepository.getPresetById("p") } returns preset(graph = brokenGraph)

        val result = useCase("p")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PipelineValidationException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given long preset name when invoke then truncates resulting pipeline name to MAX_NAME_LENGTH`() = runTest {
        val longName = "x".repeat(80)
        coEvery { presetRepository.getPresetById("p") } returns preset(name = longName)
        val saved = slot<PipelineGraph>()
        coEvery { pipelineRepository.savePipeline(capture(saved)) } returns Unit

        val result = useCase("p")

        assertTrue(result.isSuccess)
        assertEquals(60, saved.captured.name.length)
        assertFalse(saved.captured.name.endsWith(" "))
    }

    @Test
    fun `given valid preset when materialize then returns regenerated graph without persisting`() = runTest {
        coEvery { presetRepository.getPresetById("local_only_qa") } returns preset()

        val result = useCase.materialize("local_only_qa")

        assertTrue(result.isSuccess)
        val graph = result.getOrNull()!!
        // Ids are regenerated…
        assertNotEquals("template-id", graph.id)
        assertTrue(graph.nodes.none { it.id == "tpl-input" || it.id == "tpl-output" })
        // …and nothing is written to the repository (the caller decides where it lands).
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given missing preset when materialize then returns failure`() = runTest {
        coEvery { presetRepository.getPresetById("nope") } returns null

        val result = useCase.materialize("nope")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given save fails when invoke then propagates failure`() = runTest {
        coEvery { presetRepository.getPresetById("p") } returns preset()
        coEvery { pipelineRepository.savePipeline(any()) } throws RuntimeException("io")

        val result = useCase("p")

        assertTrue(result.isFailure)
        assertEquals("io", result.exceptionOrNull()?.message)
    }
}
