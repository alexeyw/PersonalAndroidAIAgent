package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePipelineUseCaseTest {

    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = DeletePipelineUseCase(pipelineRepository)

    @Test
    fun `given pipeline equals active when invoke then returns failure and skips delete`() = runTest {
        val result = useCase(pipelineId = "p1", activePipelineId = "p1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { pipelineRepository.deletePipeline(any()) }
    }

    @Test
    fun `given pipeline differs from active when invoke then deletes successfully`() = runTest {
        val result = useCase(pipelineId = "p2", activePipelineId = "p1")

        assertTrue(result.isSuccess)
        coVerify { pipelineRepository.deletePipeline("p2") }
    }

    @Test
    fun `given null active when invoke then deletes successfully`() = runTest {
        val result = useCase(pipelineId = "p2", activePipelineId = null)

        assertTrue(result.isSuccess)
        coVerify { pipelineRepository.deletePipeline("p2") }
    }

    @Test
    fun `given delete throws when invoke then returns failure`() = runTest {
        coEvery { pipelineRepository.deletePipeline("p2") } throws RuntimeException("io error")

        val result = useCase(pipelineId = "p2", activePipelineId = "p1")

        assertTrue(result.isFailure)
        assertEquals("io error", result.exceptionOrNull()?.message)
    }
}
