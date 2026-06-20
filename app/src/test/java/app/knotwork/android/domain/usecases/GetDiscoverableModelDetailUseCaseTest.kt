package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.repositories.ModelDiscoveryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [GetDiscoverableModelDetailUseCase]. */
class GetDiscoverableModelDetailUseCaseTest {

    private val repository = mockk<ModelDiscoveryRepository>()
    private val useCase = GetDiscoverableModelDetailUseCase(repository)

    @Test
    fun `given a repo id when invoked then returns the detail`() = runTest {
        val detail = DiscoverableModelDetail(
            repoId = "litert-community/gemma",
            displayName = "gemma",
            author = "litert-community",
            downloads = 1,
            likes = 0,
            license = "apache-2.0",
            gated = false,
            lastModifiedIso = null,
            modelCardUrl = "https://huggingface.co/litert-community/gemma",
            files = emptyList(),
        )
        coEvery { repository.getModelDetail("litert-community/gemma") } returns Result.success(detail)

        val result = useCase("litert-community/gemma")

        assertTrue(result.isSuccess)
        assertEquals(detail, result.getOrThrow())
    }

    @Test
    fun `given failure when invoked then propagates failure`() = runTest {
        coEvery { repository.getModelDetail(any()) } returns Result.failure(RuntimeException("boom"))

        val result = useCase("litert-community/gemma")

        assertTrue(result.isFailure)
    }
}
