package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineBundleImportOutcome
import app.knotwork.android.domain.models.PipelinePreset
import app.knotwork.android.domain.models.PresetCategory
import app.knotwork.android.domain.pipelineio.PipelineBundleJsonSerializer
import app.knotwork.android.domain.pipelineio.PipelineBundleTestFixtures.linearGraph
import app.knotwork.android.domain.repositories.PipelinePresetRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ExportPipelineBundleUseCase] — the dependency-closure walk with
 * diamond/cycle collapse, the library-then-preset resolution order, and the
 * fail-fast guards.
 */
class ExportPipelineBundleUseCaseTest {

    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var presetRepository: PipelinePresetRepository
    private lateinit var useCase: ExportPipelineBundleUseCase

    @Before
    fun setup() {
        pipelineRepository = mockk()
        presetRepository = mockk()
        // Default: nothing is a preset unless a test says so.
        coEvery { presetRepository.getPresetById(any()) } returns null
        useCase = ExportPipelineBundleUseCase(pipelineRepository, presetRepository)
    }

    private fun parsePipelines(json: String): List<String> {
        val outcome = PipelineBundleJsonSerializer.parse(json)
        assertTrue("exported bundle must parse cleanly", outcome is PipelineBundleImportOutcome.Success)
        return (outcome as PipelineBundleImportOutcome.Success).pipelines.map { it.id }
    }

    @Test
    fun `given a diamond and a cycle when export then each pipeline appears once`() = runTest {
        // root → a, b ; a → c ; b → c ; c → root (cycle). Closure = {root,a,b,c}.
        coEvery { pipelineRepository.getPipelineById("root") } returns
            linearGraph("root", targets = listOf("a", "b"))
        coEvery { pipelineRepository.getPipelineById("a") } returns linearGraph("a", targets = listOf("c"))
        coEvery { pipelineRepository.getPipelineById("b") } returns linearGraph("b", targets = listOf("c"))
        coEvery { pipelineRepository.getPipelineById("c") } returns linearGraph("c", targets = listOf("root"))

        val json = useCase("root").getOrThrow()

        assertEquals(setOf("root", "a", "b", "c"), parsePipelines(json).toSet())
        assertEquals("no pipeline is duplicated", 4, parsePipelines(json).size)
    }

    @Test
    fun `given a dependency only in the preset catalogue when export then its body is included`() = runTest {
        coEvery { pipelineRepository.getPipelineById("root") } returns
            linearGraph("root", targets = listOf("sub"))
        coEvery { pipelineRepository.getPipelineById("sub") } returns null
        coEvery { presetRepository.getPresetById("sub") } returns PipelinePreset(
            id = "sub",
            name = "Sub preset",
            description = "",
            category = PresetCategory.OTHER,
            graph = linearGraph("sub"),
            tags = emptyList(),
            isBundled = true,
        )

        val ids = parsePipelines(useCase("root").getOrThrow())

        assertEquals(setOf("root", "sub"), ids.toSet())
    }

    @Test
    fun `given an unresolvable dependency when export then fail-fast`() = runTest {
        coEvery { pipelineRepository.getPipelineById("root") } returns
            linearGraph("root", targets = listOf("ghost"))
        coEvery { pipelineRepository.getPipelineById("ghost") } returns null

        val result = useCase("root")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PipelineBundleExportException)
    }

    @Test
    fun `given a missing root when export then fail-fast`() = runTest {
        coEvery { pipelineRepository.getPipelineById("root") } returns null

        val result = useCase("root")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PipelineBundleExportException)
    }
}
