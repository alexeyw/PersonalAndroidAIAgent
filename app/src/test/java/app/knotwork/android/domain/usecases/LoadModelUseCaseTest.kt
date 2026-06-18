package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LoadModelUseCaseTest {

    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase

    @Before
    fun setup() {
        localModelRepository = mockk()
        llmInferenceEngine = mockk(relaxed = true)
        loadModelUseCase = LoadModelUseCase(localModelRepository, llmInferenceEngine)

        // Default: not initialized
        every { llmInferenceEngine.isInitialized } returns false
        every { llmInferenceEngine.currentModelPath } returns null
    }

    @Test
    fun `invoke returns Error if no active model found`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns null

        val result = loadModelUseCase()

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any()) }
    }

    @Test
    fun `invoke returns Success and does not reload if model is already loaded`() = runTest {
        val path = "/path/to/model.bin"
        val model = LocalModel(1, "Model", path, 100, true)
        coEvery { localModelRepository.getActiveModel() } returns model

        every { llmInferenceEngine.isInitialized } returns true
        every { llmInferenceEngine.currentModelPath } returns path

        val result = loadModelUseCase()

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any()) }
    }

    @Test
    fun `invoke returns Error if model file does not exist`() = runTest {
        val model = LocalModel(1, "Model", "/invalid/path/that/does/not/exist", 100, true)
        coEvery { localModelRepository.getActiveModel() } returns model

        val result = loadModelUseCase()

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any()) }
    }

    @Test
    fun `invoke initializes engine when model file exists and not loaded`() = runTest {
        // Create a temporary file to simulate existing model
        val tempFile = File.createTempFile("test_model", ".bin")
        tempFile.deleteOnExit()

        val model = LocalModel(1, "Model", tempFile.absolutePath, 100, true)
        coEvery { localModelRepository.getActiveModel() } returns model
        coEvery { llmInferenceEngine.initialize(tempFile.absolutePath) } returns Result.Success(Unit)

        val result = loadModelUseCase()

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { llmInferenceEngine.initialize(tempFile.absolutePath, enableVision = false) }
    }

    @Test
    fun `invoke re-initializes with vision when an image run hits a text-only loaded engine`() = runTest {
        val tempFile = File.createTempFile("test_model", ".bin")
        tempFile.deleteOnExit()
        val path = tempFile.absolutePath

        // Same model already loaded, but text-only — an image run must force a
        // vision-enabling re-initialization rather than reuse.
        every { llmInferenceEngine.isInitialized } returns true
        every { llmInferenceEngine.currentModelPath } returns path
        every { llmInferenceEngine.isVisionEnabled } returns false
        coEvery { llmInferenceEngine.initialize(path, enableVision = true) } returns Result.Success(Unit)

        val result = loadModelUseCase(path, requireVision = true)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { llmInferenceEngine.initialize(path, enableVision = true) }
    }

    @Test
    fun `invoke reuses an already vision-enabled engine for an image run`() = runTest {
        val path = "/path/to/model.bin"

        every { llmInferenceEngine.isInitialized } returns true
        every { llmInferenceEngine.currentModelPath } returns path
        every { llmInferenceEngine.isVisionEnabled } returns true

        val result = loadModelUseCase(path, requireVision = true)

        // Vision already on for this exact model → no re-initialization.
        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any(), any()) }
    }
}
