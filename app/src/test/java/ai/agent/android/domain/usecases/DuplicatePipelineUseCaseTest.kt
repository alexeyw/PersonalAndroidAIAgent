package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePipelineUseCaseTest {

    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = DuplicatePipelineUseCase(pipelineRepository)

    @Test
    fun `given missing pipeline when invoke then returns failure`() = runTest {
        coEvery { pipelineRepository.getPipelineById("missing") } returns null

        val result = useCase("missing")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given existing pipeline when invoke then saves duplicate with new ids and copy suffix`() = runTest {
        val source = PipelineGraph(
            id = "src",
            name = "Source",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2", label = "edge"),
            ),
        )
        coEvery { pipelineRepository.getPipelineById("src") } returns source
        val saved = slot<PipelineGraph>()
        coEvery { pipelineRepository.savePipeline(capture(saved)) } returns Unit

        val result = useCase("src")

        assertTrue(result.isSuccess)
        val duplicate = result.getOrNull()
        assertNotNull(duplicate)
        duplicate!!

        // The persisted graph matches the returned graph.
        assertEquals(duplicate, saved.captured)

        // Pipeline-level id and name follow the contract.
        assertNotEquals("src", duplicate.id)
        assertEquals("Source (copy)", duplicate.name)

        // Node ids are remapped; types / coordinates are preserved.
        assertEquals(2, duplicate.nodes.size)
        assertTrue(duplicate.nodes.none { it.id == "n1" || it.id == "n2" })
        val newInput = duplicate.nodes.first { it.type == NodeType.INPUT }
        val newOutput = duplicate.nodes.first { it.type == NodeType.OUTPUT }

        // Connections are remapped to point to the new node ids and have fresh ids.
        assertEquals(1, duplicate.connections.size)
        val connection = duplicate.connections.single()
        assertNotEquals("c1", connection.id)
        assertEquals(newInput.id, connection.sourceNodeId)
        assertEquals(newOutput.id, connection.targetNodeId)
        assertEquals("edge", connection.label)
    }

    @Test
    fun `given save failure when invoke then propagates failure`() = runTest {
        val source = PipelineGraph(
            id = "src",
            name = "Source",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(ConnectionModel("c1", "n1", "n2")),
        )
        coEvery { pipelineRepository.getPipelineById("src") } returns source
        coEvery { pipelineRepository.savePipeline(any()) } throws RuntimeException("io error")

        val result = useCase("src")

        assertTrue(result.isFailure)
        assertEquals("io error", result.exceptionOrNull()?.message)
    }
}
