package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ResolveEntryInferenceUseCaseTest {

    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ResolveEntryInferenceUseCase

    @Before
    fun setup() {
        pipelineRepository = mockk()
        settingsRepository = mockk()
        useCase = ResolveEntryInferenceUseCase(pipelineRepository, settingsRepository)
    }

    /**
     * Builds a two-node graph `INPUT -> entry` where [entryType] is the first
     * real node. Returns a graph with the given [id].
     */
    private fun graphWithEntry(id: String, entryType: NodeType?): PipelineGraph {
        val input = NodeModel("input", NodeType.INPUT, 0f, 0f)
        if (entryType == null) {
            // INPUT with no successor.
            return PipelineGraph(id = id, name = id, nodes = listOf(input), connections = emptyList())
        }
        val entry = NodeModel("entry", entryType, 0f, 0f)
        return PipelineGraph(
            id = id,
            name = id,
            nodes = listOf(input, entry),
            connections = listOf(ConnectionModel("c", "input", "entry")),
        )
    }

    @Test
    fun `given bound pipeline starts on CLOUD when invoke then returns CLOUD`() = runTest {
        coEvery { pipelineRepository.getPipelineById("bound") } returns graphWithEntry("bound", NodeType.CLOUD)

        assertEquals(EntryInferenceKind.CLOUD, useCase("bound"))
    }

    @Test
    fun `given bound pipeline starts on LITE_RT when invoke then returns LOCAL`() = runTest {
        coEvery { pipelineRepository.getPipelineById("bound") } returns graphWithEntry("bound", NodeType.LITE_RT)

        assertEquals(EntryInferenceKind.LOCAL, useCase("bound"))
    }

    @Test
    fun `given bound pipeline starts on a local system node when invoke then returns LOCAL`() = runTest {
        coEvery { pipelineRepository.getPipelineById("bound") } returns graphWithEntry("bound", NodeType.INTENT_ROUTER)

        assertEquals(EntryInferenceKind.LOCAL, useCase("bound"))
    }

    @Test
    fun `given INPUT has no successor when invoke then returns NONE`() = runTest {
        coEvery { pipelineRepository.getPipelineById("bound") } returns graphWithEntry("bound", entryType = null)

        assertEquals(EntryInferenceKind.NONE, useCase("bound"))
    }

    @Test
    fun `given graph has no INPUT node when invoke then returns NONE`() = runTest {
        val graph = PipelineGraph(
            id = "bound",
            name = "bound",
            nodes = listOf(NodeModel("a", NodeType.LITE_RT, 0f, 0f)),
            connections = emptyList(),
        )
        coEvery { pipelineRepository.getPipelineById("bound") } returns graph

        assertEquals(EntryInferenceKind.NONE, useCase("bound"))
    }

    @Test
    fun `given no bound pipeline when invoke then resolves the default pipeline`() = runTest {
        coEvery { pipelineRepository.getPipelineById("default") } returns graphWithEntry("default", NodeType.CLOUD)
        every { settingsRepository.defaultPipelineId } returns flowOf("default")

        assertEquals(EntryInferenceKind.CLOUD, useCase(sessionPipelineId = null))
    }

    @Test
    fun `given bound pipeline missing when invoke then falls back to the default pipeline`() = runTest {
        coEvery { pipelineRepository.getPipelineById("bound") } returns null
        coEvery { pipelineRepository.getPipelineById("default") } returns graphWithEntry("default", NodeType.LITE_RT)
        every { settingsRepository.defaultPipelineId } returns flowOf("default")

        assertEquals(EntryInferenceKind.LOCAL, useCase("bound"))
    }

    @Test
    fun `given neither bound nor default pipeline resolves when invoke then returns NONE`() = runTest {
        every { settingsRepository.defaultPipelineId } returns flowOf(null)

        assertEquals(EntryInferenceKind.NONE, useCase(sessionPipelineId = null))
    }
}
