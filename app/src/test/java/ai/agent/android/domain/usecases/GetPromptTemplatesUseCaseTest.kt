package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
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
    fun `invoke streams the repository list downstream`() = runTest {
        // Arrange — repository already contains every default category, so the
        // seed step is a no-op and the only side effect we expect is the
        // downstream collection seeing the same list the repository emits.
        val populated = NodeType.entries.map { type ->
            PromptTemplate(name = "Existing ${type.name}", text = "x", category = type.name)
        }
        coEvery { repository.getAllPrompts() } returns flowOf(populated)
        coEvery { repository.savePrompt(any()) } just Runs

        // Act
        val result = getPromptTemplatesUseCase().toList()

        // Assert — exactly one downstream emission carrying the repo payload.
        assertEquals(1, result.size)
        assertEquals(populated, result.first())
        // Seed didn't fire because every default category was already present.
        coVerify(exactly = 0) { repository.savePrompt(any()) }
    }

    @Test
    fun `invoke seeds missing default categories on first observation`() = runTest {
        // Arrange — repository is missing CLARIFICATION (mirrors the
        // user-reported bug). Seed should insert the missing category but
        // skip the ones already there.
        val existing = listOf(
            PromptTemplate(
                name = "Classifier",
                text = "x",
                category = NodeType.INTENT_ROUTER.name,
            ),
        )
        coEvery { repository.getAllPrompts() } returns flowOf(existing)
        coEvery { repository.savePrompt(any()) } just Runs

        // Act
        getPromptTemplatesUseCase().toList()

        // Assert — Clarification got seeded (the user-reported gap).
        coVerify {
            repository.savePrompt(
                match { it.category == NodeType.CLARIFICATION.name && it.text.isNotBlank() },
            )
        }
        // …and INTENT_ROUTER (already present) did NOT get re-seeded.
        coVerify(exactly = 0) {
            repository.savePrompt(match { it.category == NodeType.INTENT_ROUTER.name })
        }
    }
}
