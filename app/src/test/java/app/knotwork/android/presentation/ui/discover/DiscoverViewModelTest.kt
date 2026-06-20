package app.knotwork.android.presentation.ui.discover

import app.knotwork.android.domain.models.DiscoverableModelSummary
import app.knotwork.android.domain.usecases.SearchDiscoverableModelsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Unit tests for [DiscoverViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val searchModels = mockk<SearchDiscoverableModelsUseCase>()
    private val testDispatcher = StandardTestDispatcher()

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given results when initialised then status is populated`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.success(listOf(summary("litert-community/a")))

        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        assertEquals(DiscoverStatus.Populated, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.models.size)
    }

    @Test
    fun `given empty results when initialised then status is empty`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.success(emptyList())

        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        assertEquals(DiscoverStatus.Empty, vm.uiState.value.status)
    }

    @Test
    fun `given failure when initialised then status is error`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.failure(RuntimeException("boom"))

        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        assertEquals(DiscoverStatus.Error, vm.uiState.value.status)
    }

    @Test
    fun `given a query when submit search then searches with that query`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.success(emptyList())
        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        vm.onQueryChange("gemma")
        vm.onSubmitSearch()
        advanceUntilIdle()

        assertEquals("gemma", vm.uiState.value.query)
        coVerify { searchModels(query = "gemma", limit = any()) }
    }

    @Test
    fun `given refresh when triggered then clears refreshing on completion`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.success(listOf(summary("litert-community/a")))
        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        vm.onRefresh()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.refreshing)
        assertEquals(DiscoverStatus.Populated, vm.uiState.value.status)
    }

    @Test
    fun `given a populated list when refresh fails then keeps the list and clears refreshing`() = runTest {
        coEvery { searchModels(any(), any()) } returns Result.success(listOf(summary("litert-community/a")))
        val vm = DiscoverViewModel(searchModels)
        advanceUntilIdle()

        coEvery { searchModels(any(), any()) } returns Result.failure(RuntimeException("flaky"))
        vm.onRefresh()
        advanceUntilIdle()

        // The previously loaded list survives a failed pull-to-refresh.
        assertEquals(DiscoverStatus.Populated, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.models.size)
        assertEquals(false, vm.uiState.value.refreshing)
    }
}
