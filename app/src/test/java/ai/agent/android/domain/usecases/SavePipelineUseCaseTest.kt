package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.domain.models.PipelineValidationException
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
            ),
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
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n1"), // cycle
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.HasCycles) == true,
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for missing input`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "3",
            name = "Test 3",
            nodes = listOf(
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.MissingInput) == true,
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for missing output`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "4",
            name = "Test 4",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.MissingOutput) == true,
        )
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
                NodeModel("n3", NodeType.OUTPUT, 10f, 10f),
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.MultipleInputs) == true,
        )
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
                NodeModel("n3", NodeType.OUTPUT, 20f, 20f),
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.MultipleOutputs) == true,
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure for disconnected input`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "7",
            name = "Test 7",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = emptyList(), // n1 is disconnected, n2 is disconnected
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.DisconnectedInput) ==
                true,
        )
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.DisconnectedOutput) ==
                true,
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should return failure when non-INPUT node has all context flags disabled`() = runTest {
        val emptyContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        val invalidPipeline = PipelineGraph(
            id = "9",
            name = "Test 9",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.TOOL, 5f, 5f, contextConfig = emptyContext),
                NodeModel("n3", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n3"),
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertEquals(
            PipelineValidationError.NodeEmptyContext("n2"),
            exception?.errors?.firstOrNull {
                it is PipelineValidationError.NodeEmptyContext
            },
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke should not flag IF_CONDITION or QUEUE_PROCESSOR when context config is empty`() = runTest {
        val emptyContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        // IF_CONDITION and QUEUE_PROCESSOR bypass NodeContextBuilder at runtime,
        // so an empty NodeContextConfig is harmless and must not block saving.
        val pipeline = PipelineGraph(
            id = "9b",
            name = "Test 9b",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.IF_CONDITION, 5f, 5f, contextConfig = emptyContext),
                NodeModel("n3", NodeType.QUEUE_PROCESSOR, 7f, 7f, contextConfig = emptyContext),
                NodeModel("n4", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n3"),
                ConnectionModel("c3", "n3", "n4"),
            ),
        )

        val errors = pipeline.validate()
        assertFalse(
            errors.any { it is PipelineValidationError.NodeEmptyContext },
        )
    }

    @Test
    fun `invoke should not flag OUTPUT in echo mode when context config is empty`() = runTest {
        val emptyContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        // Echo-mode OUTPUT (systemPrompt = null) is a passthrough and ignores
        // contextConfig; an empty config must not produce a validation error.
        val pipeline = PipelineGraph(
            id = "9c",
            name = "Test 9c",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel(
                    id = "n2",
                    type = NodeType.OUTPUT,
                    x = 10f,
                    y = 10f,
                    systemPrompt = null,
                    contextConfig = emptyContext,
                ),
            ),
            connections = listOf(ConnectionModel("c1", "n1", "n2")),
        )

        val errors = pipeline.validate()
        assertFalse(
            errors.any { it is PipelineValidationError.NodeEmptyContext },
        )
    }

    @Test
    fun `invoke should flag OUTPUT with systemPrompt when context config is empty`() = runTest {
        val emptyContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        // OUTPUT with a configured systemPrompt acts as an LLM formatter and
        // does consume contextConfig — an empty config is a real problem.
        val pipeline = PipelineGraph(
            id = "9d",
            name = "Test 9d",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel(
                    id = "n2",
                    type = NodeType.OUTPUT,
                    x = 10f,
                    y = 10f,
                    systemPrompt = "Format the answer as markdown.",
                    contextConfig = emptyContext,
                ),
            ),
            connections = listOf(ConnectionModel("c1", "n1", "n2")),
        )

        val errors = pipeline.validate()
        assertTrue(
            errors.any {
                it is PipelineValidationError.NodeEmptyContext &&
                    it.nodeId == "n2"
            },
        )
    }

    @Test
    fun `invoke should not flag INPUT node when its context config is empty`() = runTest {
        val emptyContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        val pipeline = PipelineGraph(
            id = "10",
            name = "Test 10",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f, contextConfig = emptyContext),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
            ),
        )

        val result = useCase(pipeline)

        assertTrue(result.isSuccess)
        coVerify { pipelineRepository.savePipeline(pipeline) }
    }

    @Test
    fun `invoke should succeed when every non-INPUT node has at least one context flag enabled`() = runTest {
        val minimalContext = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = true,
            longTermMemory = false,
            toolResults = false,
        )
        val pipeline = PipelineGraph(
            id = "11",
            name = "Test 11",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.TOOL, 5f, 5f, contextConfig = minimalContext),
                NodeModel("n3", NodeType.OUTPUT, 10f, 10f, contextConfig = minimalContext),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n3"),
            ),
        )

        val result = useCase(pipeline)

        assertTrue(result.isSuccess)
        val errors = pipeline.validate()
        assertFalse(
            errors.any { it is PipelineValidationError.NodeEmptyContext },
        )
        coVerify { pipelineRepository.savePipeline(pipeline) }
    }

    @Test
    fun `invoke should return failure for unreachable and dead end nodes`() = runTest {
        val invalidPipeline = PipelineGraph(
            id = "8",
            name = "Test 8",
            nodes = listOf(
                NodeModel("n1", NodeType.INPUT, 0f, 0f),
                NodeModel("n2", NodeType.OUTPUT, 10f, 10f),
                NodeModel("n3", NodeType.TOOL, 20f, 20f),
            ),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"), // n3 is completely disconnected
            ),
        )

        val result = useCase(invalidPipeline)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? PipelineValidationException
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.UnreachableNode) == true,
        )
        assertTrue(
            exception?.errors?.contains(PipelineValidationError.DeadEndNode) == true,
        )
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }
}
