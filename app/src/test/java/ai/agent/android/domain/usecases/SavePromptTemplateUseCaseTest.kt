package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SavePromptTemplateUseCase].
 */
class SavePromptTemplateUseCaseTest {

    private lateinit var repository: PromptRepository
    private lateinit var savePromptTemplateUseCase: SavePromptTemplateUseCase

    @Before
    fun setup() {
        repository = mockk()
        savePromptTemplateUseCase = SavePromptTemplateUseCase(repository)
    }

    @Test
    fun `invoke should call savePrompt on repository`() = runTest {
        // Arrange
        val template = PromptTemplate(id = 1, name = "Test", text = "Prompt Text", category = "Test Category")
        coEvery { repository.savePrompt(any()) } returns Unit

        // Act
        savePromptTemplateUseCase(template)

        // Assert
        coVerify(exactly = 1) { repository.savePrompt(template) }
    }
}
