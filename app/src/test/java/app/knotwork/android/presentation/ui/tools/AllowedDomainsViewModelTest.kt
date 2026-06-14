package app.knotwork.android.presentation.ui.tools

import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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

/**
 * Unit tests for [AllowedDomainsViewModel] — the add-field feedback computation
 * (delegated to `HttpRequestPolicy.normalizeDomain`) and the add / remove
 * persistence gestures.
 */
class AllowedDomainsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settings: SettingsRepository = mockk()
    private val allowedFlow = MutableStateFlow<List<String>>(emptyList())

    private lateinit var viewModel: AllowedDomainsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settings.allowedHttpDomains } returns allowedFlow
        coEvery { settings.setAllowedHttpDomains(any()) } returns Unit
        viewModel = AllowedDomainsViewModel(settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a valid new host when input changes then feedback previews the normalized form`() = runTest {
        viewModel.onAddInputChange("HTTPS://Api.GitHub.com/v3/")
        advanceUntilIdle()

        assertEquals(
            AddHostFeedback.Valid("api.github.com"),
            viewModel.uiState.value.addFeedback,
        )
    }

    @Test
    fun `given an invalid host when input changes then feedback is Invalid`() = runTest {
        viewModel.onAddInputChange("https://")
        advanceUntilIdle()

        assertEquals(AddHostFeedback.Invalid, viewModel.uiState.value.addFeedback)
    }

    @Test
    fun `given input normalizing to an existing host when input changes then feedback is Duplicate`() = runTest {
        allowedFlow.value = listOf("api.openai.com")
        advanceUntilIdle()

        viewModel.onAddInputChange("API.OpenAI.com")
        advanceUntilIdle()

        assertEquals(
            AddHostFeedback.Duplicate("api.openai.com"),
            viewModel.uiState.value.addFeedback,
        )
    }

    @Test
    fun `given blank input when input changes then feedback is Idle`() = runTest {
        viewModel.onAddInputChange("   ")
        advanceUntilIdle()

        assertEquals(AddHostFeedback.Idle, viewModel.uiState.value.addFeedback)
    }

    @Test
    fun `given a valid preview when submit then appends normalized host and clears the field`() = runTest {
        allowedFlow.value = listOf("api.openai.com")
        advanceUntilIdle()
        viewModel.onAddInputChange("api.github.com")
        advanceUntilIdle()

        viewModel.onAddSubmit()
        advanceUntilIdle()

        coVerify { settings.setAllowedHttpDomains(listOf("api.openai.com", "api.github.com")) }
        assertEquals("", viewModel.uiState.value.addInput)
        assertEquals(AddHostFeedback.Idle, viewModel.uiState.value.addFeedback)
    }

    @Test
    fun `given an invalid input when submit then does not persist`() = runTest {
        viewModel.onAddInputChange("https://")
        advanceUntilIdle()

        viewModel.onAddSubmit()
        advanceUntilIdle()

        coVerify(exactly = 0) { settings.setAllowedHttpDomains(any()) }
    }

    @Test
    fun `given an existing host when remove then persists the list without it`() = runTest {
        allowedFlow.value = listOf("api.openai.com", "api.github.com")
        advanceUntilIdle()

        viewModel.onRemoveHost("api.openai.com")
        advanceUntilIdle()

        coVerify { settings.setAllowedHttpDomains(listOf("api.github.com")) }
    }
}
