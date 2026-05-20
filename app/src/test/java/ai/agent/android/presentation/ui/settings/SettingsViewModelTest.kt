package ai.agent.android.presentation.ui.settings

import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val apiKeyRepository = mockk<ApiKeyRepository>(relaxed = true)
    private val loadModelUseCase = mockk<LoadModelUseCase>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepository>(relaxed = true)
    private val crashReportingRepository = mockk<CrashReportingRepository>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup default flows for settings repository
        every { settingsRepository.temperature } returns MutableStateFlow(0.7f)
        every { settingsRepository.topK } returns MutableStateFlow(40)
        every { settingsRepository.topP } returns MutableStateFlow(0.9f)
        every { settingsRepository.maxContextLength } returns MutableStateFlow(4096)
        every { settingsRepository.systemPromptPrefix } returns MutableStateFlow("Default Prompt")
        every { settingsRepository.requiresUserConfirmation } returns MutableStateFlow(true)
        every { settingsRepository.localModelBackend } returns MutableStateFlow("CPU")
        every { settingsRepository.pipelineMaxSteps } returns MutableStateFlow(15)
        every { settingsRepository.crashReportingEnabled } returns MutableStateFlow(false)
        every { settingsRepository.memorySummaryDefaultLimit } returns MutableStateFlow(5)

        // Setup default flows for API keys repository
        every { apiKeyRepository.getOpenAIKey() } returns MutableStateFlow("sk-open")
        every { apiKeyRepository.getOpenAIModel() } returns MutableStateFlow("")
        every { apiKeyRepository.getAnthropicKey() } returns MutableStateFlow(null)
        every { apiKeyRepository.getAnthropicModel() } returns MutableStateFlow("")
        every { apiKeyRepository.getGoogleKey() } returns MutableStateFlow(null)
        every { apiKeyRepository.getGoogleModel() } returns MutableStateFlow("")
        every { apiKeyRepository.getDeepSeekKey() } returns MutableStateFlow(null)
        every { apiKeyRepository.getDeepSeekModel() } returns MutableStateFlow("")
        every { apiKeyRepository.getOllamaBaseUrl() } returns MutableStateFlow("http://localhost:11434")
        every { apiKeyRepository.getOllamaModelName() } returns MutableStateFlow("")
        every { apiKeyRepository.getOllamaContextWindowSize() } returns MutableStateFlow(4096)

        viewModel = SettingsViewModel(
            settingsRepository,
            apiKeyRepository,
            loadModelUseCase,
            localModelRepository,
            crashReportingRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correctly populated from repository`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(0.7f, state.temperature)
        assertEquals(40, state.topK)
        assertEquals(0.9f, state.topP)
        assertEquals(4096, state.maxContextLength)
        assertEquals("Default Prompt", state.systemPromptPrefix)
        assertEquals(true, state.requiresUserConfirmation)
    }

    @Test
    fun `updateTemperature calls repository`() = runTest {
        viewModel.updateTemperature(1.0f)
        advanceUntilIdle()
        coVerify { settingsRepository.setTemperature(1.0f) }
    }

    @Test
    fun `updateTopK calls repository`() = runTest {
        viewModel.updateTopK(50)
        advanceUntilIdle()
        coVerify { settingsRepository.setTopK(50) }
    }

    @Test
    fun `updateTopP calls repository`() = runTest {
        viewModel.updateTopP(0.95f)
        advanceUntilIdle()
        coVerify { settingsRepository.setTopP(0.95f) }
    }

    @Test
    fun `updateMaxContextLength calls repository`() = runTest {
        viewModel.updateMaxContextLength(8192)
        advanceUntilIdle()
        coVerify { settingsRepository.setMaxContextLength(8192) }
    }

    @Test
    fun `updateSystemPromptPrefix calls repository`() = runTest {
        viewModel.updateSystemPromptPrefix("New Prompt")
        advanceUntilIdle()
        coVerify { settingsRepository.setSystemPromptPrefix("New Prompt") }
    }

    @Test
    fun `updateRequiresUserConfirmation calls repository`() = runTest {
        viewModel.updateRequiresUserConfirmation(false)
        advanceUntilIdle()
        coVerify { settingsRepository.setRequiresUserConfirmation(false) }
    }

    @Test
    fun `updateOpenAiKey calls repository`() = runTest {
        viewModel.updateOpenAiKey("new-sk")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOpenAIKey("new-sk") }
    }

    @Test
    fun `updateAnthropicKey calls repository`() = runTest {
        viewModel.updateAnthropicKey("anthropic-key")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setAnthropicKey("anthropic-key") }
    }

    @Test
    fun `updateGoogleKey calls repository`() = runTest {
        viewModel.updateGoogleKey("google-key")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setGoogleKey("google-key") }
    }

    @Test
    fun `updateDeepSeekKey calls repository`() = runTest {
        viewModel.updateDeepSeekKey("deepseek-key")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setDeepSeekKey("deepseek-key") }
    }

    @Test
    fun `updateOllamaBaseUrl calls repository`() = runTest {
        viewModel.updateOllamaBaseUrl("http://192.168.0.1:11434")
        advanceUntilIdle()
        coVerify { apiKeyRepository.setOllamaBaseUrl("http://192.168.0.1:11434") }
    }

    @Test
    fun `updatePipelineMaxSteps calls repository with valid value`() = runTest {
        viewModel.updatePipelineMaxSteps(25)
        advanceUntilIdle()
        coVerify { settingsRepository.setPipelineMaxSteps(25) }
    }

    @Test
    fun `updatePipelineMaxSteps coerces value below minimum`() = runTest {
        viewModel.updatePipelineMaxSteps(1)
        advanceUntilIdle()
        coVerify { settingsRepository.setPipelineMaxSteps(5) }
    }

    @Test
    fun `updatePipelineMaxSteps coerces value above maximum`() = runTest {
        viewModel.updatePipelineMaxSteps(200)
        advanceUntilIdle()
        coVerify { settingsRepository.setPipelineMaxSteps(100) }
    }

    @Test
    fun `updateCrashReportingEnabled writes settings flag and toggles crash repository on`() = runTest {
        viewModel.updateCrashReportingEnabled(true)
        advanceUntilIdle()
        coVerify { settingsRepository.setCrashReportingEnabled(true) }
        coVerify { crashReportingRepository.setEnabled(true) }
    }

    @Test
    fun `updateCrashReportingEnabled writes settings flag and toggles crash repository off`() = runTest {
        viewModel.updateCrashReportingEnabled(false)
        advanceUntilIdle()
        coVerify { settingsRepository.setCrashReportingEnabled(false) }
        coVerify { crashReportingRepository.setEnabled(false) }
    }

    @Test
    fun `crashReportingEnabled is populated from repository in initial state`() = runTest {
        every { settingsRepository.crashReportingEnabled } returns MutableStateFlow(true)
        val vm = SettingsViewModel(
            settingsRepository,
            apiKeyRepository,
            loadModelUseCase,
            localModelRepository,
            crashReportingRepository,
        )
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.crashReportingEnabled)
    }

    @Test
    fun `pipelineMaxSteps is populated from repository in initial state`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns MutableStateFlow(42)
        val vm = SettingsViewModel(
            settingsRepository,
            apiKeyRepository,
            loadModelUseCase,
            localModelRepository,
            crashReportingRepository,
        )
        advanceUntilIdle()
        assertEquals(42, vm.uiState.value.pipelineMaxSteps)
    }

    @Test
    fun `memorySummaryDefaultLimit is populated from repository in initial state`() = runTest {
        every { settingsRepository.memorySummaryDefaultLimit } returns MutableStateFlow(12)
        val vm = SettingsViewModel(
            settingsRepository,
            apiKeyRepository,
            loadModelUseCase,
            localModelRepository,
            crashReportingRepository,
        )
        advanceUntilIdle()
        assertEquals(12, vm.uiState.value.memorySummaryDefaultLimit)
    }

    @Test
    fun `updateMemorySummaryDefaultLimit calls repository with valid value`() = runTest {
        viewModel.updateMemorySummaryDefaultLimit(20)
        advanceUntilIdle()
        coVerify { settingsRepository.setMemorySummaryDefaultLimit(20) }
    }

    @Test
    fun `updateMemorySummaryDefaultLimit coerces value below minimum`() = runTest {
        viewModel.updateMemorySummaryDefaultLimit(0)
        advanceUntilIdle()
        coVerify { settingsRepository.setMemorySummaryDefaultLimit(1) }
    }

    @Test
    fun `updateMemorySummaryDefaultLimit coerces value above maximum`() = runTest {
        viewModel.updateMemorySummaryDefaultLimit(999)
        advanceUntilIdle()
        coVerify { settingsRepository.setMemorySummaryDefaultLimit(50) }
    }

    @Test
    fun `updateOllamaBaseUrl marks invalid flag when blank`() = runTest {
        viewModel.updateOllamaBaseUrl("   ")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(true, state.ollamaBaseUrlInvalid)
        coVerify { apiKeyRepository.setOllamaBaseUrl(null) }
    }

    @Test
    fun `updateOllamaBaseUrl clears invalid flag once a URL is supplied`() = runTest {
        viewModel.updateOllamaBaseUrl("")
        advanceUntilIdle()
        viewModel.updateOllamaBaseUrl("http://x:1")
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.ollamaBaseUrlInvalid)
    }

    @Test
    fun `updateOpenAiModel marks row as pending until repository persists`() = runTest {
        viewModel.updateOpenAiModel("gpt-4o-mini")
        // Before advancing the dispatcher the launch{} block has not yet
        // cleared the pending flag — the row should still be tracked.
        assertEquals(true, viewModel.uiState.value.pendingRowIds.contains("openai"))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.pendingRowIds.contains("openai"))
        coVerify { apiKeyRepository.setOpenAIModel("gpt-4o-mini") }
    }
}
