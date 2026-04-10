package ai.agent.android.presentation.ui.models

import ai.agent.android.domain.models.LocalModel
import ai.agent.android.data.network.AndroidModelDownloadManager
import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.ModelDownloadManager
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ModelsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {

    private val localModelRepository: LocalModelRepository = mockk(relaxed = true)
    private val downloadManager: ModelDownloadManager = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private lateinit var viewModel: ModelsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Default mock for initial state
        every { localModelRepository.getAllModels() } returns flowOf(emptyList())
        
        viewModel = ModelsViewModel(localModelRepository, downloadManager, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads downloaded models and active model`() = runTest {
        val models = listOf(
            LocalModel(id = 1, name = "Model 1", path = "/path", size = 100, isActive = false),
            LocalModel(id = 2, name = "Model 2", path = "/path", size = 100, isActive = true)
        )
        
        every { localModelRepository.getAllModels() } returns flowOf(models)
        
        // Re-initialize to pick up the new flow
        viewModel = ModelsViewModel(localModelRepository, downloadManager, settingsRepository)
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(models, state.downloadedModels)
        assertEquals(models[1], state.activeModel)
    }

    @Test
    fun `onCustomUrlChanged updates state`() {
        val newUrl = "http://example.com/model.bin"
        viewModel.onCustomUrlChanged(newUrl)
        
        assertEquals(newUrl, viewModel.uiState.value.customUrlInput)
        assertEquals(null, viewModel.uiState.value.downloadError)
    }

    @Test
    fun `observeAuthToken sets initial auth token`() = runTest {
        every { settingsRepository.huggingFaceAuthToken } returns flowOf("test-token-123")
        viewModel = ModelsViewModel(localModelRepository, downloadManager, settingsRepository)
        advanceUntilIdle()

        assertEquals("test-token-123", viewModel.uiState.value.authTokenInput)
    }

    @Test
    fun `onAuthTokenChanged updates state and saves to repository`() = runTest {
        viewModel.onAuthTokenChanged("new-token-456")
        advanceUntilIdle()

        assertEquals("new-token-456", viewModel.uiState.value.authTokenInput)
        coVerify { settingsRepository.setHuggingFaceAuthToken("new-token-456") }
    }

    @Test
    fun `onAuthTokenChanged with blank string saves null to repository`() = runTest {
        viewModel.onAuthTokenChanged("   ")
        advanceUntilIdle()

        assertEquals("   ", viewModel.uiState.value.authTokenInput)
        coVerify { settingsRepository.setHuggingFaceAuthToken(null) }
    }

    @Test
    fun `startDownload updates state through download lifecycle`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        
        every { downloadManager.downloadModel(url, fileName) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Downloading(50),
            DownloadState.Success("/local/path")
        )
        
        viewModel.startDownload(url, fileName)
        
        // Assert initial downloading state
        var state = viewModel.uiState.value
        assertEquals(true, state.isDownloading)
        
        advanceUntilIdle()
        
        // Assert final state after success
        state = viewModel.uiState.value
        assertEquals(false, state.isDownloading)
        assertEquals(null, state.downloadProgress)
    }

    @Test
    fun `startDownload handles error state`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        val error = AndroidModelDownloadManager.DownloadError("Network failed")
        
        every { downloadManager.downloadModel(url, fileName) } returns flowOf(
            DownloadState.Error(error)
        )
        
        viewModel.startDownload(url, fileName)
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(false, state.isDownloading)
        assertEquals(error, state.downloadError)
    }

    @Test
    fun `setActiveModel calls repository`() = runTest {
        viewModel.setActiveModel(1L)
        advanceUntilIdle()
        coVerify { localModelRepository.setActiveModel(1L) }
    }

    @Test
    fun `clearError sets downloadError to null`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        val error = AndroidModelDownloadManager.DownloadError("Network failed")
        
        every { downloadManager.downloadModel(url, fileName) } returns flowOf(
            DownloadState.Error(error)
        )
        
        viewModel.startDownload(url, fileName)
        advanceUntilIdle()
        
        assertEquals(error, viewModel.uiState.value.downloadError)
        
        viewModel.clearError()
        advanceUntilIdle()
        
        assertEquals(null, viewModel.uiState.value.downloadError)
    }
}