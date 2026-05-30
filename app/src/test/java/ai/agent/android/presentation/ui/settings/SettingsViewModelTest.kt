package ai.agent.android.presentation.ui.settings

import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.Identity
import ai.agent.android.domain.models.MemoryStats
import ai.agent.android.domain.models.TestProbeResult
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.IdentityRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.ClearAllMemoryUseCase
import ai.agent.android.domain.usecases.ExportMemoryBaseUseCase
import ai.agent.android.domain.usecases.GetSystemPromptVariableCatalogUseCase
import ai.agent.android.domain.usecases.MemoryImportUseCase
import ai.agent.android.domain.usecases.PromptVariableCatalogEntry
import ai.agent.android.domain.usecases.ReembedAllMemoriesUseCase
import ai.agent.android.domain.usecases.ResetSamplingDefaultsUseCase
import ai.agent.android.domain.usecases.TestBackendUseCase
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the redesigned [SettingsViewModel] (Phase 22 / Task 9).
 *
 * Coverage:
 *  - identity load + variable catalog load on init,
 *  - mutator methods route through their respective repositories,
 *  - restart-required detection flips when the backend / Ollama URL changes,
 *  - destructive typed-confirm gate (Clear memory / Reset settings),
 *  - reset-settings clears every preference back to defaults.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val context = mockk<Context>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val apiKeys = mockk<ApiKeyRepository>(relaxed = true)
    private val localModels = mockk<LocalModelRepository>(relaxed = true)
    private val memory = mockk<MemoryRepository>(relaxed = true)
    private val identity = mockk<IdentityRepository>(relaxed = true)
    private val crashReporting = mockk<CrashReportingRepository>(relaxed = true)
    private val testBackend = mockk<TestBackendUseCase>(relaxed = true)
    private val resetSampling = mockk<ResetSamplingDefaultsUseCase>(relaxed = true)
    private val clearMemory = mockk<ClearAllMemoryUseCase>(relaxed = true)
    private val exportMemory = mockk<ExportMemoryBaseUseCase>(relaxed = true)
    private val memoryImport = mockk<MemoryImportUseCase>(relaxed = true)
    private val reembed = mockk<ReembedAllMemoriesUseCase>(relaxed = true)
    private val variableCatalog = mockk<GetSystemPromptVariableCatalogUseCase>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { context.getString(any()) } returns "anonymous"
        every { context.getString(any<Int>(), *anyVararg()) } returns "message"

        every { settings.systemPromptPrefix } returns MutableStateFlow("")
        every { settings.toolApprovalPolicy } returns MutableStateFlow(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settings.blockDestructiveTools } returns MutableStateFlow(false)
        every { settings.blockNetworkFromLocalModel } returns MutableStateFlow(false)
        every { settings.pipelineMaxSteps } returns MutableStateFlow(20)
        every { settings.temperature } returns MutableStateFlow(0.7f)
        every { settings.topK } returns MutableStateFlow(40)
        every { settings.topP } returns MutableStateFlow(0.9f)
        every { settings.repetitionPenalty } returns MutableStateFlow(1.1f)
        every { settings.maxContextLength } returns MutableStateFlow(4096)
        every { settings.localModelBackend } returns MutableStateFlow("CPU")
        every { settings.lastTestProbeResult } returns MutableStateFlow<TestProbeResult?>(null)
        every { settings.autoSummarizeThreshold } returns MutableStateFlow(0.8f)
        every { settings.longRunningTaskNotificationsEnabled } returns MutableStateFlow(true)
        every { settings.crashReportingEnabled } returns MutableStateFlow(false)
        every { settings.verboseMemoryLoggingEnabled } returns MutableStateFlow(false)

        every { localModels.observeActiveModelMeta() } returns MutableStateFlow(null)
        every { memory.observeStats() } returns MutableStateFlow(MemoryStats.EMPTY)

        every { apiKeys.getOpenAIKey() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getOpenAIModel() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getAnthropicKey() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getAnthropicModel() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getGoogleKey() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getGoogleModel() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getDeepSeekKey() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getDeepSeekModel() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getOllamaBaseUrl() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getOllamaModelName() } returns MutableStateFlow<String?>(null)
        every { apiKeys.getOllamaContextWindowSize() } returns MutableStateFlow(4096)

        coEvery { identity.getIdentity(any()) } returns Identity(
            displayName = "Anonymous · this device",
            deviceId = "4f3a-92d1",
            keystoreAvailable = true,
        )
        coEvery { variableCatalog() } returns listOf(
            PromptVariableCatalogEntry("\$DATE", "20 May 2026"),
            PromptVariableCatalogEntry("\$TIME", "09:30"),
        )

        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads identity and variable catalog`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("Anonymous · this device", state.identity?.displayName)
        assertEquals(2, state.variableCatalog.size)
        assertEquals("\$DATE", state.variableCatalog.first().placeholder)
    }

    @Test
    fun `setToolApprovalPolicy routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setToolApprovalPolicy(ToolApprovalPolicy.AllCalls)
        advanceUntilIdle()
        coVerify { settings.setToolApprovalPolicy(ToolApprovalPolicy.AllCalls) }
    }

    @Test
    fun `setBlockDestructiveTools routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setBlockDestructiveTools(true)
        advanceUntilIdle()
        coVerify { settings.setBlockDestructiveTools(true) }
    }

    @Test
    fun `setBlockNetworkFromLocalModel routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setBlockNetworkFromLocalModel(true)
        advanceUntilIdle()
        coVerify { settings.setBlockNetworkFromLocalModel(true) }
    }

    @Test
    fun `setRepetitionPenalty routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setRepetitionPenalty(1.5f)
        advanceUntilIdle()
        coVerify { settings.setRepetitionPenalty(1.5f) }
    }

    @Test
    fun `setAutoSummarizeThreshold converts percent to fraction`() = runTest {
        advanceUntilIdle()
        viewModel.setAutoSummarizeThreshold(75)
        advanceUntilIdle()
        coVerify { settings.setAutoSummarizeThreshold(0.75f) }
    }

    @Test
    fun `resetSamplingDefaults invokes use case and surfaces snackbar`() = runTest {
        advanceUntilIdle()
        viewModel.resetSamplingDefaults()
        advanceUntilIdle()
        coVerify { resetSampling() }
        assertNotNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `changing backend flips restartRequired`() = runTest {
        val backendFlow = MutableStateFlow("CPU")
        every { settings.localModelBackend } returns backendFlow
        viewModel = newViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restartRequired)
        backendFlow.value = "GPU"
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restartRequired)
    }

    @Test
    fun `setting Ollama base URL from blank to value flips restartRequired`() = runTest {
        val ollamaUrlFlow = MutableStateFlow<String?>(null)
        every { apiKeys.getOllamaBaseUrl() } returns ollamaUrlFlow
        viewModel = newViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restartRequired)
        ollamaUrlFlow.value = "http://192.168.1.42:11434"
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restartRequired)
    }

    @Test
    fun `clearing Ollama base URL from value to blank flips restartRequired`() = runTest {
        val ollamaUrlFlow = MutableStateFlow<String?>("http://192.168.1.42:11434")
        every { apiKeys.getOllamaBaseUrl() } returns ollamaUrlFlow
        viewModel = newViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restartRequired)
        ollamaUrlFlow.value = null
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restartRequired)
    }

    @Test
    fun `acknowledgeRestart resets the baseline`() = runTest {
        val backendFlow = MutableStateFlow("CPU")
        every { settings.localModelBackend } returns backendFlow
        viewModel = newViewModel()
        advanceUntilIdle()
        backendFlow.value = "GPU"
        advanceUntilIdle()
        viewModel.acknowledgeRestart()
        assertFalse(viewModel.uiState.value.restartRequired)
    }

    @Test
    fun `stageClearMemory sets pending destructive action`() = runTest {
        advanceUntilIdle()
        viewModel.stageClearMemory()
        assertEquals(PendingDestructiveAction.ClearMemory, viewModel.uiState.value.pendingDestructive)
    }

    @Test
    fun `confirmDestructive without matching keyword is a no-op`() = runTest {
        every { context.getString(ai.agent.android.R.string.settings_destructive_typed_keyword) } returns "yes"
        advanceUntilIdle()
        viewModel.stageClearMemory()
        viewModel.updateDestructiveTypedInput("nope")
        viewModel.confirmDestructive()
        advanceUntilIdle()
        coVerify(exactly = 0) { clearMemory() }
        assertNotNull(viewModel.uiState.value.pendingDestructive)
    }

    @Test
    fun `confirmDestructive with matching keyword clears memory`() = runTest {
        every { context.getString(ai.agent.android.R.string.settings_destructive_typed_keyword) } returns "yes"
        advanceUntilIdle()
        viewModel.stageClearMemory()
        viewModel.updateDestructiveTypedInput("yes")
        viewModel.confirmDestructive()
        advanceUntilIdle()
        coVerify { clearMemory() }
        assertNull(viewModel.uiState.value.pendingDestructive)
    }

    @Test
    fun `confirmDestructive ResetSettings resets multiple preferences`() = runTest {
        every { context.getString(ai.agent.android.R.string.settings_destructive_typed_keyword) } returns "yes"
        advanceUntilIdle()
        viewModel.stageResetSettings()
        viewModel.updateDestructiveTypedInput("YES")
        viewModel.confirmDestructive()
        advanceUntilIdle()
        coVerify { resetSampling() }
        coVerify { settings.setToolApprovalPolicy(ToolApprovalPolicy.DEFAULT) }
        coVerify { settings.setBlockDestructiveTools(false) }
        coVerify { settings.setBlockNetworkFromLocalModel(false) }
        coVerify { settings.setAutoSummarizeThreshold(SettingsDefaults.AUTO_SUMMARIZE_THRESHOLD_DEFAULT) }
    }

    @Test
    fun `cancelDestructive clears pending state`() = runTest {
        advanceUntilIdle()
        viewModel.stageClearMemory()
        viewModel.cancelDestructive()
        assertNull(viewModel.uiState.value.pendingDestructive)
        assertEquals("", viewModel.uiState.value.destructiveTypedInput)
    }

    @Test
    fun `setLongRunningTaskNotificationsEnabled routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setLongRunningTaskNotificationsEnabled(false)
        advanceUntilIdle()
        coVerify { settings.setLongRunningTaskNotificationsEnabled(false) }
    }

    @Test
    fun `setCrashReportingEnabled syncs both settings and crashReporting`() = runTest {
        advanceUntilIdle()
        viewModel.setCrashReportingEnabled(true)
        advanceUntilIdle()
        coVerify { settings.setCrashReportingEnabled(true) }
        coVerify { crashReporting.setEnabled(true) }
    }

    @Test
    fun `setVerboseMemoryLoggingEnabled routes through repository`() = runTest {
        advanceUntilIdle()
        viewModel.setVerboseMemoryLoggingEnabled(true)
        advanceUntilIdle()
        coVerify { settings.setVerboseMemoryLoggingEnabled(true) }
    }

    @Test
    fun `verboseMemoryLoggingEnabled flow is mirrored into uiState`() = runTest {
        every { settings.verboseMemoryLoggingEnabled } returns MutableStateFlow(true)
        val vm = newViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.verboseMemoryLoggingEnabled)
    }

    @Test
    fun `runReembed forwards progress to uiState`() = runTest {
        coEvery { reembed() } returns flowOf(0.5f, 1f)
        advanceUntilIdle()
        viewModel.runReembed()
        advanceUntilIdle()
        // Final emission of 1f clears the in-flight indicator (null), per VM contract.
        assertNull(viewModel.uiState.value.reembedProgress)
    }

    @Test
    fun `runBackendProbe persists outcome and surfaces snackbar`() = runTest {
        coEvery { testBackend() } returns TestProbeResult(
            tokensGenerated = 100,
            durationMs = 500L,
            timestampMs = 0L,
            success = true,
        )
        advanceUntilIdle()
        viewModel.runBackendProbe()
        advanceUntilIdle()
        coVerify { testBackend() }
        assertNotNull(viewModel.uiState.value.snackbarMessage)
    }

    private fun newViewModel(): SettingsViewModel = SettingsViewModel(
        appContext = context,
        settingsRepository = settings,
        apiKeyRepository = apiKeys,
        localModelRepository = localModels,
        memoryRepository = memory,
        identityRepository = identity,
        crashReportingRepository = crashReporting,
        testBackendUseCase = testBackend,
        resetSamplingDefaultsUseCase = resetSampling,
        clearAllMemoryUseCase = clearMemory,
        exportMemoryBaseUseCase = exportMemory,
        memoryImportUseCase = memoryImport,
        reembedAllMemoriesUseCase = reembed,
        getSystemPromptVariableCatalogUseCase = variableCatalog,
    )
}
