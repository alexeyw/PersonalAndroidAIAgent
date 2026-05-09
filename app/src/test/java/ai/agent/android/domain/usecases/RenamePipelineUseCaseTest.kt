package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamePipelineUseCaseTest {

    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = RenamePipelineUseCase(pipelineRepository)

    @Test
    fun `given blank name when invoke then returns failure`() = runTest {
        val result = useCase("any-id", "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { pipelineRepository.getPipelineById(any()) }
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given name longer than 60 chars when invoke then returns failure`() = runTest {
        val longName = "a".repeat(61)

        val result = useCase("any-id", longName)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given missing pipeline when invoke then returns failure`() = runTest {
        coEvery { pipelineRepository.getPipelineById("missing") } returns null

        val result = useCase("missing", "Renamed")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `given existing pipeline when invoke then saves with trimmed name`() = runTest {
        val existing = PipelineGraph(id = "p1", name = "Old", updatedAt = 1L)
        coEvery { pipelineRepository.getPipelineById("p1") } returns existing

        val result = useCase("p1", "  Brand New  ")

        assertTrue(result.isSuccess)
        coVerify {
            pipelineRepository.savePipeline(
                match { saved ->
                    saved.id == "p1" && saved.name == "Brand New" && saved.updatedAt > 1L
                },
            )
        }
    }

    @Test
    fun `given save failure when invoke then propagates failure`() = runTest {
        val existing = PipelineGraph(id = "p1", name = "Old")
        coEvery { pipelineRepository.getPipelineById("p1") } returns existing
        coEvery { pipelineRepository.savePipeline(any()) } throws RuntimeException("disk full")

        val result = useCase("p1", "New")

        assertTrue(result.isFailure)
        assertEquals("disk full", result.exceptionOrNull()?.message)
    }
}
