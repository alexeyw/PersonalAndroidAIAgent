package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.usecases.DeletePromptTemplateUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptLibraryViewModelTest {

    private lateinit var getPromptTemplatesUseCase: GetPromptTemplatesUseCase
    private lateinit var savePromptTemplateUseCase: SavePromptTemplateUseCase
    private lateinit var deletePromptTemplateUseCase: DeletePromptTemplateUseCase
    private lateinit var viewModel: PromptLibraryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getPromptTemplatesUseCase = mockk()
        savePromptTemplateUseCase = mockk()
        deletePromptTemplateUseCase = mockk()

        val initialPrompts = listOf(
            PromptTemplate(1, "Test", "Content", "TOOL")
        )
        coEvery { getPromptTemplatesUseCase() } returns flowOf(initialPrompts)

        viewModel = PromptLibraryViewModel(
            getPromptTemplatesUseCase,
            savePromptTemplateUseCase,
            deletePromptTemplateUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads prompts successfully`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.promptTemplates.size)
        assertEquals("Test", state.promptTemplates[0].name)
    }

    @Test
    fun `savePrompt calls use case`() = runTest {
        val prompt = PromptTemplate(2, "New", "New content", "SUMMARY")
        coEvery { savePromptTemplateUseCase(any()) } returns Unit

        viewModel.savePrompt(prompt)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { savePromptTemplateUseCase(prompt) }
    }

    @Test
    fun `deletePrompt calls use case`() = runTest {
        coEvery { deletePromptTemplateUseCase(any()) } returns Unit

        viewModel.deletePrompt(1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deletePromptTemplateUseCase(1) }
    }
}
