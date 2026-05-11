package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptSegment
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.usecases.DeletePromptTemplateUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptLibraryViewModelTest {

    private lateinit var getPromptTemplatesUseCase: GetPromptTemplatesUseCase
    private lateinit var savePromptTemplateUseCase: SavePromptTemplateUseCase
    private lateinit var deletePromptTemplateUseCase: DeletePromptTemplateUseCase
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private lateinit var providerDate: PromptVariableProvider
    private lateinit var providerTime: PromptVariableProvider
    private lateinit var viewModel: PromptLibraryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getPromptTemplatesUseCase = mockk()
        savePromptTemplateUseCase = mockk()
        deletePromptTemplateUseCase = mockk()

        val initialPrompts = listOf(
            PromptTemplate(1, "Test", "Content", "TOOL"),
        )
        coEvery { getPromptTemplatesUseCase() } returns flowOf(initialPrompts)

        promptTemplateEngine = mockk()
        providerDate = mockk {
            every { key() } returns "DATE"
            coEvery { resolve() } returns "01 May 2026"
        }
        providerTime = mockk {
            every { key() } returns "TIME"
            coEvery { resolve() } returns "15:30"
        }

        viewModel = PromptLibraryViewModel(
            getPromptTemplatesUseCase,
            savePromptTemplateUseCase,
            deletePromptTemplateUseCase,
            promptTemplateEngine,
            setOf(providerDate, providerTime),
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

    @Test
    fun `availableVariables are derived from injected providers and sorted`() {
        val state = viewModel.uiState.value

        assertEquals(listOf("\$DATE", "\$TIME"), state.availableVariables)
    }

    @Test
    fun `requestPromptPreview transitions to Ready with engine segments`() = runTest {
        val template = "Hi \$DATE"
        val segments = listOf(
            PromptSegment.Literal("Hi "),
            PromptSegment.Resolved("DATE", "01 May 2026"),
        )
        coEvery { promptTemplateEngine.renderSegments(template, any()) } returns segments

        viewModel.requestPromptPreview(template)
        testDispatcher.scheduler.advanceUntilIdle()

        val readyState = viewModel.uiState.value.previewState
        assertTrue(readyState is PromptPreviewState.Ready)
        assertEquals(segments, (readyState as PromptPreviewState.Ready).segments)
        coVerify { promptTemplateEngine.renderSegments(template, any()) }
    }

    @Test
    fun `dismissPromptPreview resets state to Hidden`() = runTest {
        coEvery { promptTemplateEngine.renderSegments(any(), any()) } returns emptyList()
        viewModel.requestPromptPreview("anything")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissPromptPreview()

        assertEquals(PromptPreviewState.Hidden, viewModel.uiState.value.previewState)
    }
}
