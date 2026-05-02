package ai.agent.android.data.prompt

import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.repositories.LocalModelRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ModelVariableProvider].
 */
class ModelVariableProviderTest {

    private val localModelRepository: LocalModelRepository = mockk()
    private val provider = ModelVariableProvider(localModelRepository)

    @Test
    fun `given key when called then returns MODEL`() {
        assertEquals("MODEL", provider.key())
    }

    @Test
    fun `given active model when resolve then returns model name`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns LocalModel(
            id = 1L,
            name = "Gemma 2B",
            path = "/data/model.tflite",
            size = 1L,
            isActive = true,
        )

        val result = provider.resolve()

        assertEquals("Gemma 2B", result)
    }

    @Test
    fun `given no active model when resolve then returns empty string`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns null

        val result = provider.resolve()

        assertEquals("", result)
    }
}
