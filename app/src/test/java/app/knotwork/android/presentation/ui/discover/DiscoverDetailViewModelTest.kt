package app.knotwork.android.presentation.ui.discover

import app.knotwork.android.data.network.AndroidModelDownloadManager.DownloadError
import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.models.DiscoverableModelFile
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.GetDiscoverableModelDetailUseCase
import app.knotwork.android.domain.usecases.InstallDiscoveredModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for [DiscoverDetailViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverDetailViewModelTest {

    private val getDetail = mockk<GetDiscoverableModelDetailUseCase>()
    private val installModel = mockk<InstallDiscoveredModelUseCase>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val file = DiscoverableModelFile(
        fileName = "gemma.litertlm",
        sizeBytes = 2_048L,
        resolveUrl = "https://huggingface.co/litert-community/gemma/resolve/main/gemma.litertlm",
        isInstalled = false,
    )

    private fun detail(files: List<DiscoverableModelFile> = listOf(file)) = DiscoverableModelDetail(
        repoId = "litert-community/gemma",
        displayName = "gemma",
        author = "litert-community",
        downloads = 10,
        likes = 2,
        license = "apache-2.0",
        gated = false,
        lastModifiedIso = null,
        modelCardUrl = "https://huggingface.co/litert-community/gemma",
        files = files,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settings.huggingFaceAuthToken } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createBound(): DiscoverDetailViewModel {
        val vm = DiscoverDetailViewModel(getDetail, installModel, settings)
        vm.bind("litert-community/gemma")
        return vm
    }

    @Test
    fun `given detail loads when bound then status is loaded with installed flags`() = runTest {
        val installedFile = file.copy(fileName = "gemma-gpu.litertlm", isInstalled = true)
        coEvery { getDetail(any()) } returns Result.success(detail(files = listOf(file, installedFile)))

        val vm = createBound()
        advanceUntilIdle()

        assertEquals(DiscoverDetailStatus.Loaded, vm.uiState.value.status)
        assertTrue("gemma-gpu.litertlm" in vm.uiState.value.installed)
    }

    @Test
    fun `given load failure when bound then status is error`() = runTest {
        coEvery { getDetail(any()) } returns Result.failure(RuntimeException("boom"))

        val vm = createBound()
        advanceUntilIdle()

        assertEquals(DiscoverDetailStatus.Error, vm.uiState.value.status)
    }

    @Test
    fun `given install clicked when fired then opens the license dialog`() = runTest {
        coEvery { getDetail(any()) } returns Result.success(detail())
        val vm = createBound()
        advanceUntilIdle()

        vm.onInstallClick("gemma.litertlm")

        assertEquals("gemma.litertlm", vm.uiState.value.pendingLicenseFileName)
    }

    @Test
    fun `given license confirmed when install succeeds then marks file installed`() = runTest {
        coEvery { getDetail(any()) } returns Result.success(detail())
        every { installModel(any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Downloading(50),
            DownloadState.Success("/data/gemma.litertlm"),
        )
        val vm = createBound()
        advanceUntilIdle()

        vm.onInstallClick("gemma.litertlm")
        vm.onLicenseConfirm("gemma.litertlm")
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.pendingLicenseFileName)
        assertTrue("gemma.litertlm" in vm.uiState.value.installed)
    }

    @Test
    fun `given gated refusal when install fails then emits a gated failure`() = runTest {
        coEvery { getDetail(any()) } returns Result.success(detail())
        every { installModel(any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Error(DownloadError("Server returned code: 401")),
        )
        val vm = createBound()
        advanceUntilIdle()

        // Subscribe before the event is emitted; runCurrent() lets the
        // collector reach the suspension point so the emission isn't missed.
        val eventDeferred = async { vm.installEvents.first() }
        runCurrent()

        vm.onLicenseConfirm("gemma.litertlm")
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is DiscoverInstallEvent.Failed && event.gated)
    }

    @Test
    fun `given token change when fired then persists the token`() = runTest {
        coEvery { getDetail(any()) } returns Result.success(detail())
        val vm = createBound()
        advanceUntilIdle()

        vm.onTokenChange("hf_abc")
        advanceUntilIdle()

        assertEquals("hf_abc", vm.uiState.value.tokenInput)
        coVerify { settings.setHuggingFaceAuthToken("hf_abc") }
    }

    @Test
    fun `given token reveal toggled when fired then flips the flag`() = runTest {
        coEvery { getDetail(any()) } returns Result.success(detail())
        val vm = createBound()
        advanceUntilIdle()

        vm.onToggleTokenReveal()

        assertTrue(vm.uiState.value.tokenRevealed)
    }
}
