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
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.HasCycles) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for missing input`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "3",
            name = "Test 3",
            nodes = listOf(
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f)
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.MissingInput) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for missing output`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "4",
            name = "Test 4",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f)
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.MissingOutput) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for multiple inputs`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "5",
            name = "Test 5",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.INPUT, 0f, 0f),
                NodeModel("n3", NodeType.OUTPUT, 10f, 10f)
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.MultipleInputs) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for multiple outputs`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "6",
            name = "Test 6",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
                NodeModel("n3", NodeType.OUTPUT, 20f, 20f)
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.MultipleOutputs) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for disconnected input`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "7",
            name = "Test 7",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f)
            ),
            connections = emptyList() // n1 is disconnected, n2 is disconnected
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.DisconnectedInput) == true)
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.DisconnectedOutput) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for unreachable and dead end nodes`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "8",
            name = "Test 8",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
                NodeModel("n3", NodeType.TOOL, 20f, 20f)
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2") // n3 is completely disconnected
            )
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? ai.agent.android.domain.models.PipelineValidationException
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.UnreachableNode) == true)
        assertTrue(exception?.errors?.contains(ai.agent.android.domain.models.PipelineValidationError.DeadEndNode) == true)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }
}
