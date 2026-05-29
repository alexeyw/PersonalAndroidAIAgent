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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavePipelineAsPresetUseCaseTest {

    private val repository: PipelinePresetRepository = mockk(relaxed = true)
    private val useCase = SavePipelineAsPresetUseCase(repository)

    private fun validGraph(): PipelineGraph = PipelineGraph(
        id = "p1",
        name = "Pipeline",
        nodes = listOf(
            NodeModel(
                id = "i",
                type = NodeType.INPUT,
                x = 0f,
                y = 0f,
                contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
            ),
            NodeModel(
                id = "o",
                type = NodeType.OUTPUT,
                x = 100f,
                y = 0f,
                contextConfig = NodeContextConfig.defaultForType(NodeType.OUTPUT),
            ),
        ),
        connections = listOf(ConnectionModel(id = "c", sourceNodeId = "i", targetNodeId = "o")),
    )

    @Test
    fun `given valid input when invoke then persists preset with isBundled false`() = runTest {
        val captured = slot<PipelinePreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        val result = useCase(
            graph = validGraph(),
            name = "My preset",
            description = "Saved from current graph",
            category = PresetCategory.LOCAL,
            tags = listOf("custom", "saved"),
        )

        assertTrue(result.isSuccess)
        val id = result.getOrNull()
        assertNotNull(id)
        assertEquals(id, captured.captured.id)
        assertEquals("My preset", captured.captured.name)
        assertEquals("Saved from current graph", captured.captured.description)
        assertEquals(PresetCategory.LOCAL, captured.captured.category)
        assertEquals(listOf("custom", "saved"), captured.captured.tags)
        assertFalse(captured.captured.isBundled)
    }

    @Test
    fun `given blank name when invoke then returns failure without touching repository`() = runTest {
        val result = useCase(
            graph = validGraph(),
            name = "   ",
            description = "",
            category = PresetCategory.OTHER,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given name longer than limit when invoke then returns failure`() = runTest {
        val result = useCase(
            graph = validGraph(),
            name = "x".repeat(61),
            description = "",
            category = PresetCategory.OTHER,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given graph with validation errors when invoke then returns PipelineValidationException`() = runTest {
        val broken = PipelineGraph(id = "p", name = "p") // no nodes → missing INPUT/OUTPUT

        val result = useCase(
            graph = broken,
            name = "preset",
            description = "",
            category = PresetCategory.OTHER,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PipelineValidationException)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given dirty tags when invoke then trims dedups and drops blanks`() = runTest {
        val captured = slot<PipelinePreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        val result = useCase(
            graph = validGraph(),
            name = "preset",
            description = "  desc  ",
            category = PresetCategory.HYBRID,
            tags = listOf("  alpha ", "beta", "", "alpha", "   "),
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("alpha", "beta"), captured.captured.tags)
        assertEquals("desc", captured.captured.description)
    }

    @Test
    fun `given repository throws when invoke then surfaces failure`() = runTest {
        coEvery { repository.saveUserPreset(any()) } throws RuntimeException("db down")

        val result = useCase(
            graph = validGraph(),
            name = "preset",
            description = "",
            category = PresetCategory.OTHER,
        )

        assertTrue(result.isFailure)
        assertEquals("db down", result.exceptionOrNull()?.message)
    }
}
