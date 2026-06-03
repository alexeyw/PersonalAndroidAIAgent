package ai.agent.android.presentation.ui.settings.provider

import ai.agent.android.domain.models.ProviderId
import ai.agent.android.domain.repositories.ApiKeyRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProviderDetailViewModel] — the standalone editor backing the
 * Settings → External providers detail screen. Closes the
 * `presentation.ui.settings.provider` coverage gap recorded in
 * `docs/coverage-baseline.md` (the ViewModel shipped at 0 % in Phase 23).
 *
 * The tests exercise both directions of the contract:
 * - `bind(providerId)` wires the relevant [ApiKeyRepository] read flows into
 *   [ProviderDetailUiState] for every provider.
 * - Each `update*` mutator persists through the repository, applying the
 *   "blank → null" normalisation and the Ollama-specific validation /
 *   context-window fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var viewModel: ProviderDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Relaxed so the suspend setters are stubbed as no-ops; bind() reads are
        // overridden per test below.
        apiKeyRepository = mockk(relaxed = true)
        viewModel = ProviderDetailViewModel(apiKeyRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given OpenAi when bind then key and model flow into state`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf("sk-openai")
        every { apiKeyRepository.getOpenAIModel() } returns flowOf("gpt-4o")

        viewModel.bind(ProviderId.OpenAi)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("sk-openai", state.openAiKey)
        assertEquals("gpt-4o", state.openAiModel)
    }

    @Test
    fun `given Anthropic when bind then key and model flow into state`() = runTest {
        every { apiKeyRepository.getAnthropicKey() } returns flowOf("sk-ant")
        every { apiKeyRepository.getAnthropicModel() } returns flowOf("claude")

        viewModel.bind(ProviderId.Anthropic)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("sk-ant", state.anthropicKey)
        assertEquals("claude", state.anthropicModel)
    }

    @Test
    fun `given Google when bind then key and model flow into state`() = runTest {
        every { apiKeyRepository.getGoogleKey() } returns flowOf("g-key")
        every { apiKeyRepository.getGoogleModel() } returns flowOf("gemini")

        viewModel.bind(ProviderId.Google)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("g-key", state.googleKey)
        assertEquals("gemini", state.googleModel)
    }

    @Test
    fun `given DeepSeek when bind then key and model flow into state`() = runTest {
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf("ds-key")
        every { apiKeyRepository.getDeepSeekModel() } returns flowOf("deepseek-chat")

        viewModel.bind(ProviderId.DeepSeek)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ds-key", state.deepSeekKey)
        assertEquals("deepseek-chat", state.deepSeekModel)
    }

    @Test
    fun `given Ollama when bind then base url model and context window flow into state`() = runTest {
        every { apiKeyRepository.getOllamaBaseUrl() } returns flowOf("http://10.0.0.2:11434")
        every { apiKeyRepository.getOllamaModelName() } returns flowOf("llama3")
        every { apiKeyRepository.getOllamaContextWindowSize() } returns flowOf(8192)

        viewModel.bind(ProviderId.Ollama)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("http://10.0.0.2:11434", state.ollamaBaseUrl)
        assertEquals("llama3", state.ollamaModel)
        assertEquals("8192", state.ollamaContextWindow)
    }

    @Test
    fun `given null repository value when bind then state field is empty string`() = runTest {
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getOpenAIModel() } returns flowOf(null)

        viewModel.bind(ProviderId.OpenAi)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.openAiKey)
        assertEquals("", viewModel.uiState.value.openAiModel)
    }

    @Test
    fun `given non-blank value when updateOpenAiKey then persists value`() = runTest {
        viewModel.updateOpenAiKey("sk-new")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOpenAIKey("sk-new") }
    }

    @Test
    fun `given blank value when updateOpenAiKey then persists null`() = runTest {
        viewModel.updateOpenAiKey("   ")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOpenAIKey(null) }
    }

    @Test
    fun `when update model mutators then each persists trimmed-to-null value`() = runTest {
        viewModel.updateOpenAiModel("gpt-4o")
        viewModel.updateAnthropicKey("a")
        viewModel.updateAnthropicModel("claude")
        viewModel.updateGoogleKey("g")
        viewModel.updateGoogleModel("gemini")
        viewModel.updateDeepSeekKey("d")
        viewModel.updateDeepSeekModel("deepseek")
        viewModel.updateOllamaModel("llama3")
        advanceUntilIdle()

        coVerify { apiKeyRepository.setOpenAIModel("gpt-4o") }
        coVerify { apiKeyRepository.setAnthropicKey("a") }
        coVerify { apiKeyRepository.setAnthropicModel("claude") }
        coVerify { apiKeyRepository.setGoogleKey("g") }
        coVerify { apiKeyRepository.setGoogleModel("gemini") }
        coVerify { apiKeyRepository.setDeepSeekKey("d") }
        coVerify { apiKeyRepository.setDeepSeekModel("deepseek") }
        coVerify { apiKeyRepository.setOllamaModelName("llama3") }
    }

    @Test
    fun `given blank base url when updateOllamaBaseUrl then flags invalid and persists null`() = runTest {
        viewModel.updateOllamaBaseUrl("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.ollamaBaseUrlInvalid)
        assertEquals("", viewModel.uiState.value.ollamaBaseUrl)
        coVerify { apiKeyRepository.setOllamaBaseUrl(null) }
    }

    @Test
    fun `given valid base url when updateOllamaBaseUrl then clears invalid and persists value`() = runTest {
        viewModel.updateOllamaBaseUrl("http://localhost:11434")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.ollamaBaseUrlInvalid)
        assertEquals("http://localhost:11434", viewModel.uiState.value.ollamaBaseUrl)
        coVerify { apiKeyRepository.setOllamaBaseUrl("http://localhost:11434") }
    }

    @Test
    fun `given numeric input when updateOllamaContextWindow then persists parsed size`() = runTest {
        viewModel.updateOllamaContextWindow("16384")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOllamaContextWindowSize(16384) }
    }

    @Test
    fun `given non-numeric input when updateOllamaContextWindow then persists default size`() = runTest {
        viewModel.updateOllamaContextWindow("not-a-number")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOllamaContextWindowSize(4096) }
    }
}
