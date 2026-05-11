package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GetPromptTemplatesUseCase].
 */
class GetPromptTemplatesUseCaseTest {

    private lateinit var repository: PromptRepository
    private lateinit var getPromptTemplatesUseCase: GetPromptTemplatesUseCase

    @Before
    fun setup() {
        repository = mockk()
        getPromptTemplatesUseCase = GetPromptTemplatesUseCase(repository)
    }

    @Test
    fun `invoke should return flow from repository`() = runTest {
        // Arrange
        val expectedPrompts = listOf(
            PromptTemplate(id = 1, name = "Test1", text = "Text1", category = "Cat1"),
            PromptTemplate(id = 2, name = "Test2", text = "Text2", category = "Cat2"),
        )
        coEvery { repository.getAllPrompts() } returns flowOf(expectedPrompts)
        coEvery { repository.getPromptsCount() } returns 2

        // Act
        val result = getPromptTemplatesUseCase().toList()

        // Assert
        assertEquals(1, result.size)
        assertEquals(expectedPrompts, result.first())
        coVerify(exactly = 1) { repository.getAllPrompts() }
        coVerify(exactly = 1) { repository.getPromptsCount() }
    }
}
