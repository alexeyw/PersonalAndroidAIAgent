package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.DiscoverableModelSummary
import app.knotwork.android.domain.repositories.ModelDiscoveryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SearchDiscoverableModelsUseCase]. */
class SearchDiscoverableModelsUseCaseTest {

    private val repository = mockk<ModelDiscoveryRepository>()
    private val useCase = SearchDiscoverableModelsUseCase(repository)

    private fun summary(repoId: String) = DiscoverableModelSummary(
        repoId = repoId,
        displayName = repoId.substringAfterLast('/'),
        author = "litert-community",
        downloads = 1,
        likes = 0,
        license = "apache-2.0",
        gated = false,
        litertFileCount = 1,
        lastModifiedIso = null,
    )

    @Test
    fun `given results when invoked then returns them`() = runTest {
        val models = listOf(summary("litert-community/a"))
        coEvery { repository.searchModels(any(), any()) } returns Result.success(models)

        val result = useCase(query = "a")

        assertTrue(result.isSuccess)
        assertEquals(models, result.getOrThrow())
    }

    @Test
    fun `given blank query when invoked then normalises it to null`() = runTest {
        coEvery { repository.searchModels(null, any()) } returns Result.success(emptyList())

        useCase(query = "   ")

        coVerify { repository.searchModels(query = null, limit = any()) }
    }

    @Test
    fun `given failure when invoked then propagates failure`() = runTest {
        coEvery { repository.searchModels(any(), any()) } returns Result.failure(RuntimeException("boom"))

        val result = useCase(query = null)

        assertTrue(result.isFailure)
    }
}
