package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.DiscoverableModelFile
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [InstallDiscoveredModelUseCase]. */
class InstallDiscoveredModelUseCaseTest {

    private val downloadManager = mockk<ModelDownloadManager>()
    private val localModelRepository = mockk<LocalModelRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val useCase = InstallDiscoveredModelUseCase(downloadManager, localModelRepository, settingsRepository)

    private val file = DiscoverableModelFile(
        fileName = "gemma.litertlm",
        sizeBytes = 2_048L,
        resolveUrl = "https://huggingface.co/litert-community/gemma/resolve/main/gemma.litertlm",
        isInstalled = false,
    )

    @Test
    fun `given success when invoked then inserts the model with the hub size and token`() = runTest {
        every { settingsRepository.huggingFaceAuthToken } returns flowOf("hf_token")
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Downloading(50),
            DownloadState.Success("/data/gemma.litertlm"),
        )
        coEvery { localModelRepository.findByFileName(file.fileName) } returns null
        val inserted = slot<LocalModel>()
        coEvery { localModelRepository.insertModel(capture(inserted)) } returns 7L

        val states = useCase(file).toList()

        assertTrue(states.last() is DownloadState.Success)
        coVerify {
            downloadManager.downloadModel(
                url = file.resolveUrl,
                fileName = file.fileName,
                authToken = "hf_token",
            )
        }
        coVerify { localModelRepository.insertModel(any()) }
        assertEquals("gemma.litertlm", inserted.captured.name)
        assertEquals("/data/gemma.litertlm", inserted.captured.path)
        assertEquals(2_048L, inserted.captured.size)
        assertEquals(false, inserted.captured.isActive)
    }

    @Test
    fun `given an existing row for the file when invoked then updates instead of inserting a duplicate`() = runTest {
        every { settingsRepository.huggingFaceAuthToken } returns flowOf(null)
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Success("/data/gemma.litertlm"),
        )
        val existing = LocalModel(id = 9L, name = file.fileName, path = "/old/path", size = 0L, isActive = true)
        coEvery { localModelRepository.findByFileName(file.fileName) } returns existing
        val updated = slot<LocalModel>()
        coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

        useCase(file).toList()

        coVerify(exactly = 0) { localModelRepository.insertModel(any()) }
        coVerify { localModelRepository.updateModel(any()) }
        // Path/size refreshed, identity and active flag preserved.
        assertEquals(9L, updated.captured.id)
        assertEquals("/data/gemma.litertlm", updated.captured.path)
        assertEquals(2_048L, updated.captured.size)
        assertEquals(true, updated.captured.isActive)
    }

    @Test
    fun `given error when invoked then does not insert a model`() = runTest {
        every { settingsRepository.huggingFaceAuthToken } returns flowOf(null)
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Error(mockk(relaxed = true)),
        )
        coEvery { localModelRepository.insertModel(any()) } returns 1L

        val states = useCase(file).toList()

        assertTrue(states.last() is DownloadState.Error)
        coVerify(exactly = 0) { localModelRepository.insertModel(any()) }
    }

    @Test
    fun `given no stored token when invoked then passes null token`() = runTest {
        every { settingsRepository.huggingFaceAuthToken } returns flowOf(null)
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(DownloadState.Pending)

        useCase(file).first()

        coVerify { downloadManager.downloadModel(any(), any(), authToken = null) }
    }
}
