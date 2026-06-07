package app.knotwork.android.presentation.ui.settings

import android.content.Context
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.models.Identity
import app.knotwork.android.domain.models.MemoryStats
import app.knotwork.android.domain.models.TestProbeResult
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.CrashReportingRepository
import app.knotwork.android.domain.repositories.IdentityRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.EmbeddingProvider
import app.knotwork.android.domain.usecases.ClearAllMemoryUseCase
import app.knotwork.android.domain.usecases.ExportMemoryBaseUseCase
import app.knotwork.android.domain.usecases.GetSystemPromptVariableCatalogUseCase
import app.knotwork.android.domain.usecases.MemoryImportUseCase
import app.knotwork.android.domain.usecases.PromptVariableCatalogEntry
import app.knotwork.android.domain.usecases.ReembedAllMemoriesUseCase
import app.knotwork.android.domain.usecases.ResetSamplingDefaultsUseCase
import app.knotwork.android.domain.usecases.TestBackendUseCase
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

    private val useProvider = fakeProvider(EmbeddingProvider.ID_USE, "On-device (USE)")
    private val openAiProvider = fakeProvider(EmbeddingProvider.ID_OPENAI_3_SMALL, "OpenAI (3-small)")
    private val embeddingProviders: Map<String, EmbeddingProvider> = mapOf(
        EmbeddingProvider.ID_OPENAI_3_SMALL to openAiProvider,
        EmbeddingProvider.ID_USE to useProvider,
    )

    private lateinit var viewModel: SettingsViewModel
    private val dispatcher = StandardTestDispatcher()

    private fun fakeProvider(providerId: String, name: String): EmbeddingProvider = mockk(relaxed = true) {
        every { id } returns providerId
        every { displayName } returns name
    }

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
        every { settings.autoExtractEnabled } returns MutableStateFlow(true)
        every { settings.memorySearchTopK } returns MutableStateFlow(SettingsDefaults.MEMORY_SEARCH_TOP_K_DEFAULT)
        every { settings.memorySearchThreshold } returns
            MutableStateFlow(SettingsDefaults.MEMORY_SEARCH_THRESHOLD_DEFAULT)
        every { settings.memoryRecencyHalfLifeDays } returns
            MutableStateFlow(SettingsDefaults.MEMORY_RECENCY_HALF_LIFE_DAYS_DEFAULT)
        every { settings.memoryCompactionEnabled } returns MutableStateFlow(true)
        every { settings.memoryCompactionAgeDays } returns
            MutableStateFlow(SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_DEFAULT)
        every { settings.maxMemoryChunks } returns MutableStateFlow(SettingsDefaults.MAX_MEMORY_CHUNKS_DEFAULT)
        every { settings.activeEmbeddingProviderId } returns MutableStateFlow(EmbeddingProvider.ID_USE)
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

    // ─── Memory tuning: observation ────────────────────────────────────────

    @Test
    fun `init observes memory tuning preferences and builds embedding options`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SettingsDefaults.MEMORY_SEARCH_TOP_K_DEFAULT, state.memorySearchTopK)
        assertEquals(SettingsDefaults.MEMORY_SEARCH_THRESHOLD_DEFAULT, state.memorySearchThreshold)
        assertEquals(SettingsDefaults.MEMORY_RECENCY_HALF_LIFE_DAYS_DEFAULT, state.memoryRecencyHalfLifeDays)
        assertTrue(state.memoryCompactionEnabled)
        assertEquals(SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_DEFAULT, state.memoryCompactionAgeDays)
        assertEquals(SettingsDefaults.MAX_MEMORY_CHUNKS_DEFAULT, state.maxMemoryChunks)
        assertEquals(EmbeddingProvider.ID_USE, state.activeEmbeddingProviderId)
        // On-device USE is hoisted to the top regardless of map iteration order.
        assertEquals(EmbeddingProvider.ID_USE, state.embeddingProviderOptions.first().id)
        assertEquals(embeddingProviders.size, state.embeddingProviderOptions.size)
    }

    // ─── Memory tuning: valid edits persist ────────────────────────────────

    @Test
    fun `setMemorySearchTopK within range persists and clears error`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchTopK(10)
        advanceUntilIdle()
        coVerify { settings.setMemorySearchTopK(10) }
        assertNull(viewModel.uiState.value.memoryValidationError)
    }

    @Test
    fun `setMemorySearchThreshold within range persists`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchThreshold(0.5f)
        advanceUntilIdle()
        coVerify { settings.setMemorySearchThreshold(0.5f) }
        assertNull(viewModel.uiState.value.memoryValidationError)
    }

    @Test
    fun `setMemoryRecencyHalfLifeDays within range persists`() = runTest {
        advanceUntilIdle()
        viewModel.setMemoryRecencyHalfLifeDays(60)
        advanceUntilIdle()
        coVerify { settings.setMemoryRecencyHalfLifeDays(60) }
    }

    @Test
    fun `setMemoryCompactionEnabled persists`() = runTest {
        advanceUntilIdle()
        viewModel.setMemoryCompactionEnabled(false)
        advanceUntilIdle()
        coVerify { settings.setMemoryCompactionEnabled(false) }
        assertNull(viewModel.uiState.value.memoryValidationError)
    }

    @Test
    fun `setMemoryCompactionAgeDays within range persists`() = runTest {
        advanceUntilIdle()
        viewModel.setMemoryCompactionAgeDays(45)
        advanceUntilIdle()
        coVerify { settings.setMemoryCompactionAgeDays(45) }
    }

    @Test
    fun `setMaxMemoryChunks within range persists`() = runTest {
        advanceUntilIdle()
        viewModel.setMaxMemoryChunks(8_000)
        advanceUntilIdle()
        coVerify { settings.setMaxMemoryChunks(8_000) }
    }

    @Test
    fun `setActiveEmbeddingProviderId for known provider persists`() = runTest {
        advanceUntilIdle()
        viewModel.setActiveEmbeddingProviderId(EmbeddingProvider.ID_OPENAI_3_SMALL)
        advanceUntilIdle()
        coVerify { settings.setActiveEmbeddingProviderId(EmbeddingProvider.ID_OPENAI_3_SMALL) }
        assertNull(viewModel.uiState.value.memoryValidationError)
    }

    // ─── Memory tuning: out-of-range edits are rejected ────────────────────

    @Test
    fun `setMemorySearchTopK above max is rejected with validation error and not persisted`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchTopK(SettingsDefaults.MEMORY_SEARCH_TOP_K_MAX + 1)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.SearchTopK, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMemorySearchTopK(any()) }
    }

    @Test
    fun `setMemorySearchTopK below min is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchTopK(SettingsDefaults.MEMORY_SEARCH_TOP_K_MIN - 1)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.SearchTopK, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMemorySearchTopK(any()) }
    }

    @Test
    fun `setMemorySearchThreshold out of range is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchThreshold(SettingsDefaults.MEMORY_SEARCH_THRESHOLD_MAX + 0.1f)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.SearchThreshold, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMemorySearchThreshold(any()) }
    }

    @Test
    fun `setMemoryRecencyHalfLifeDays out of range is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setMemoryRecencyHalfLifeDays(SettingsDefaults.MEMORY_RECENCY_HALF_LIFE_DAYS_MAX + 1)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.RecencyHalfLife, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMemoryRecencyHalfLifeDays(any()) }
    }

    @Test
    fun `setMemoryCompactionAgeDays out of range is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setMemoryCompactionAgeDays(SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_MIN - 1)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.CompactionAge, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMemoryCompactionAgeDays(any()) }
    }

    @Test
    fun `setMaxMemoryChunks out of range is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setMaxMemoryChunks(SettingsDefaults.MAX_MEMORY_CHUNKS_MAX + 1)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.MaxChunks, viewModel.uiState.value.memoryValidationError)
        coVerify(exactly = 0) { settings.setMaxMemoryChunks(any()) }
    }

    @Test
    fun `setActiveEmbeddingProviderId for unknown id is rejected`() = runTest {
        advanceUntilIdle()
        viewModel.setActiveEmbeddingProviderId("nonexistent_provider")
        advanceUntilIdle()
        assertEquals(
            MemoryValidationError.UnknownEmbeddingProvider,
            viewModel.uiState.value.memoryValidationError,
        )
        coVerify(exactly = 0) { settings.setActiveEmbeddingProviderId(any()) }
    }

    @Test
    fun `clearMemoryValidationError resets the error and a valid edit clears it`() = runTest {
        advanceUntilIdle()
        viewModel.setMemorySearchTopK(SettingsDefaults.MEMORY_SEARCH_TOP_K_MAX + 5)
        advanceUntilIdle()
        assertEquals(MemoryValidationError.SearchTopK, viewModel.uiState.value.memoryValidationError)

        viewModel.clearMemoryValidationError()
        assertNull(viewModel.uiState.value.memoryValidationError)

        // A subsequent in-range edit also keeps the error cleared.
        viewModel.setMemorySearchTopK(3)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.memoryValidationError)
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
        every { context.getString(app.knotwork.android.R.string.settings_destructive_typed_keyword) } returns "yes"
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
        every { context.getString(app.knotwork.android.R.string.settings_destructive_typed_keyword) } returns "yes"
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
        every { context.getString(app.knotwork.android.R.string.settings_destructive_typed_keyword) } returns "yes"
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
    fun `confirmDestructive ResetSettings also reverts memory tuning to defaults`() = runTest {
        every { context.getString(app.knotwork.android.R.string.settings_destructive_typed_keyword) } returns "yes"
        advanceUntilIdle()
        viewModel.stageResetSettings()
        viewModel.updateDestructiveTypedInput("yes")
        viewModel.confirmDestructive()
        advanceUntilIdle()
        coVerify { settings.setAutoExtractEnabled(SettingsDefaults.AUTO_EXTRACT_ENABLED_DEFAULT) }
        coVerify { settings.setMemorySearchTopK(SettingsDefaults.MEMORY_SEARCH_TOP_K_DEFAULT) }
        coVerify { settings.setMemorySearchThreshold(SettingsDefaults.MEMORY_SEARCH_THRESHOLD_DEFAULT) }
        coVerify { settings.setMemoryRecencyHalfLifeDays(SettingsDefaults.MEMORY_RECENCY_HALF_LIFE_DAYS_DEFAULT) }
        coVerify { settings.setMemoryCompactionEnabled(SettingsDefaults.MEMORY_COMPACTION_ENABLED_DEFAULT) }
        coVerify { settings.setMemoryCompactionAgeDays(SettingsDefaults.MEMORY_COMPACTION_AGE_DAYS_DEFAULT) }
        coVerify { settings.setMaxMemoryChunks(SettingsDefaults.MAX_MEMORY_CHUNKS_DEFAULT) }
        coVerify { settings.setActiveEmbeddingProviderId(SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT) }
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
        embeddingProviders = embeddingProviders,
    )
}
