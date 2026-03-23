package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SavePipelineUseCaseTest {

    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = SavePipelineUseCase(pipelineRepository)

    @Test
    fun `invoke should save valid DAG pipeline`() = runTest {
        val validPipeline = PipelineGraph(
            id = "1",
            name = "Test 1",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f)
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2")
            )
        )

        val result = useCase(validPipeline)

        assertTrue(result.isSuccess)
        coVerify { pipelineRepository.savePipeline(validPipeline) }
    }

    @Test
    fun `invoke should return failure for invalid DAG pipeline`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "2",
            name = "Test 2",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f)
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n1") // cycle
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }
}
