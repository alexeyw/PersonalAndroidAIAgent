package ai.agent.android.presentation.ui.settings

import ai.agent.android.domain.repositories.SettingsRepository
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

        viewModel = SettingsViewModel(settingsRepository)
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
}
