package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ImportCollisionResolution
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.pipelineio.PipelineBundleJsonSerializer
import app.knotwork.android.domain.pipelineio.PipelineBundleTestFixtures.linearGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ImportPipelineBundleUseCase] — the prepare (parse + validate +
 * collision-detect) and persist (id policy + atomic write) steps.
 */
class ImportPipelineBundleUseCaseTest {

    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var useCase: ImportPipelineBundleUseCase

    @Before
    fun setup() {
        pipelineRepository = mockk()
        coEvery { pipelineRepository.getPipelineById(any()) } returns null
        coEvery { pipelineRepository.savePipelines(any()) } returns Unit
        useCase = ImportPipelineBundleUseCase(pipelineRepository)
    }

    private fun bundleOf(vararg graphs: PipelineGraph): String =
        PipelineBundleJsonSerializer.serialize(graphs.toList(), exportedAt = 0L)

    @Test
    fun `given malformed bundle when prepare then Failure`() = runTest {
        assertTrue(useCase.prepare("{ not json") is PipelineBundlePrepareResult.Failure)
    }

    @Test
    fun `given a structurally invalid graph when prepare then Failure`() = runTest {
        // A graph that parses but fails validate(): INPUT with no OUTPUT.
        val invalid = PipelineGraph(
            id = "bad",
            name = "bad",
            nodes = listOf(NodeModel(id = "in", type = NodeType.INPUT, x = 0f, y = 0f)),
            connections = emptyList(),
            updatedAt = 0L,
        )

        assertTrue(useCase.prepare(bundleOf(invalid)) is PipelineBundlePrepareResult.Failure)
    }

    @Test
    fun `given a collision when prepare then it is reported and nothing persisted`() = runTest {
        coEvery { pipelineRepository.getPipelineById("root") } returns linearGraph("root")
        val json = bundleOf(linearGraph("root", targets = listOf("sub")), linearGraph("sub"))

        val prepared = useCase.prepare(json)

        assertTrue(prepared is PipelineBundlePrepareResult.Ready)
        assertEquals(listOf("root"), (prepared as PipelineBundlePrepareResult.Ready).collidingIds)
        coVerify(exactly = 0) { pipelineRepository.savePipelines(any()) }
    }

    @Test
    fun `given REPLACE when persist then ids are preserved and saved atomically`() = runTest {
        val saved: CapturingSlot<List<PipelineGraph>> = slot()
        coEvery { pipelineRepository.savePipelines(capture(saved)) } returns Unit
        val pipelines = listOf(linearGraph("root", targets = listOf("sub")), linearGraph("sub"))

        val result = useCase.persist(pipelines, ImportCollisionResolution.REPLACE)

        assertEquals(2, result.getOrThrow())
        assertEquals(setOf("root", "sub"), saved.captured.map { it.id }.toSet())
        coVerify(exactly = 1) { pipelineRepository.savePipelines(any()) }
    }

    @Test
    fun `given IMPORT_AS_COPY when persist then ids are regenerated and references stay consistent`() = runTest {
        val saved: CapturingSlot<List<PipelineGraph>> = slot()
        coEvery { pipelineRepository.savePipelines(capture(saved)) } returns Unit
        val pipelines = listOf(linearGraph("root", targets = listOf("sub")), linearGraph("sub"))

        useCase.persist(pipelines, ImportCollisionResolution.IMPORT_AS_COPY)

        val savedIds = saved.captured.map { it.id }.toSet()
        assertTrue("original ids must not be reused", savedIds.none { it == "root" || it == "sub" })
        // The remapped root target must resolve to the remapped sub-pipeline id.
        val newSubId = saved.captured.first { it.name == "name-sub" }.id
        val remappedTarget = saved.captured.first { it.name == "name-root" }
            .nodes.first { it.type == NodeType.PIPELINE }.targetPipelineId
        assertEquals(newSubId, remappedTarget)
    }

    @Test
    fun `given save failure when persist then Result failure`() = runTest {
        coEvery { pipelineRepository.savePipelines(any()) } throws RuntimeException("disk full")

        val result = useCase.persist(listOf(linearGraph("root")), ImportCollisionResolution.REPLACE)

        assertTrue(result.isFailure)
    }
}
