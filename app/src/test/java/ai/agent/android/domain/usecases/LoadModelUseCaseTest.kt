package ai.agent.android.domain.usecases

import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
        llmInferenceEngine = mockk()
        loadModelUseCase = LoadModelUseCase(localModelRepository, llmInferenceEngine)
    }

    @Test
    fun `invoke returns Error if no active model found`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns null

        val result = loadModelUseCase()

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any()) }
    }

    @Test
    fun `invoke returns Error if model file does not exist`() = runTest {
        val model = LocalModelEntity(1, "Model", "/invalid/path/that/does/not/exist", 100, true)
        coEvery { localModelRepository.getActiveModel() } returns model

        val result = loadModelUseCase()

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { llmInferenceEngine.initialize(any()) }
    }

    @Test
    fun `invoke initializes engine when model file exists`() = runTest {
        // Create a temporary file to simulate existing model
        val tempFile = File.createTempFile("test_model", ".bin")
        tempFile.deleteOnExit()

        val model = LocalModelEntity(1, "Model", tempFile.absolutePath, 100, true)
        coEvery { localModelRepository.getActiveModel() } returns model
        coEvery { llmInferenceEngine.initialize(tempFile.absolutePath) } returns Result.Success(Unit)

        val result = loadModelUseCase()

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { llmInferenceEngine.initialize(tempFile.absolutePath) }
    }
}
