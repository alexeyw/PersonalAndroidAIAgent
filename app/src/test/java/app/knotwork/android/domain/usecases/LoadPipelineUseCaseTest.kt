package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadPipelineUseCaseTest {

    private val pipelineRepository: PipelineRepository = mockk()
    private val useCase = LoadPipelineUseCase(pipelineRepository)

    @Test
    fun `observeAllPipelines returns flow from repository`() = runTest {
        val pipelines = listOf(PipelineGraph(id = "1", name = "Test 1"), PipelineGraph(id = "2", name = "Test 2"))
        every { pipelineRepository.getAllPipelines() } returns flowOf(pipelines)

        val result = useCase.observeAllPipelines()

        result.collect { list ->
            assertEquals(2, list.size)
        }
    }

    @Test
    fun `getPipelineById returns pipeline from repository`() = runTest {
        val pipeline = PipelineGraph(id = "1", name = "Test 1")
        coEvery { pipelineRepository.getPipelineById("1") } returns pipeline

        val result = useCase.getPipelineById("1")

        assertEquals(pipeline, result)
    }
}
