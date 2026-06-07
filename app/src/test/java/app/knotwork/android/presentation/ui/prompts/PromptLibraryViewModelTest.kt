package app.knotwork.android.presentation.ui.prompts

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.prompt.PromptSegment
import app.knotwork.android.domain.prompt.PromptTemplateEngine
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.repositories.PromptPresetRepository
import app.knotwork.android.domain.usecases.SavePromptAsPresetUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptLibraryViewModelTest {

    private lateinit var promptPresetRepository: PromptPresetRepository
    private lateinit var savePromptAsPresetUseCase: SavePromptAsPresetUseCase
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private lateinit var providerDate: PromptVariableProvider
    private lateinit var providerTime: PromptVariableProvider
    private lateinit var viewModel: PromptLibraryViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val bundledA = PromptPreset(
        id = "bundled-a",
        name = "Concise assistant",
        description = "Short answers.",
        nodeType = NodeType.LITE_RT,
        systemPrompt = "You are concise.",
        tags = listOf("concise"),
        isBundled = true,
    )
    private val userA = PromptPreset(
        id = "user-a",
        name = "My LiteRt preset",
        description = "",
        nodeType = NodeType.LITE_RT,
        systemPrompt = "You are friendly.",
        isBundled = false,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        promptPresetRepository = mockk(relaxed = true) {
            every { getBundledPresets() } returns flowOf(listOf(bundledA))
            every { getUserPresets() } returns flowOf(listOf(userA))
        }
        savePromptAsPresetUseCase = mockk()
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
            promptPresetRepository,
            savePromptAsPresetUseCase,
            promptTemplateEngine,
            setOf(providerDate, providerTime),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads bundled and user presets`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(listOf(bundledA), state.bundledPresets)
        assertEquals(listOf(userA), state.userPresets)
    }

    @Test
    fun `deletePrompt forwards user-preset deletions to repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deletePrompt(userA.id)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { promptPresetRepository.deleteUserPreset(userA.id) }
    }

    @Test
    fun `deletePrompt refuses to delete a bundled preset`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deletePrompt(bundledA.id)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { promptPresetRepository.deleteUserPreset(any()) }
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

    @Test
    fun `saveEditor passes existingId for an in-place update`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery {
            savePromptAsPresetUseCase(
                systemPrompt = any(),
                name = any(),
                description = any(),
                nodeType = any(),
                tags = any(),
                existingId = any(),
            )
        } returns Result.success(userA.id)

        viewModel.openEditor(promptId = userA.id)
        viewModel.onEditorBodyChange("Updated body.")
        viewModel.saveEditor()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            savePromptAsPresetUseCase(
                systemPrompt = "Updated body.",
                name = userA.name,
                description = userA.description,
                nodeType = NodeType.LITE_RT,
                tags = userA.tags,
                existingId = userA.id,
            )
        }
    }

    @Test
    fun `saveEditor refuses invalid category`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openEditor(promptId = null)
        viewModel.onEditorCategoryChange("TOOL") // not LLM-driven
        viewModel.onEditorBodyChange("body")
        viewModel.saveEditor()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) {
            savePromptAsPresetUseCase(
                systemPrompt = any(),
                name = any(),
                description = any(),
                nodeType = any(),
                tags = any(),
                existingId = any(),
            )
        }
    }
}
