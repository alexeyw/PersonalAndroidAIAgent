package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies [OnboardingViewModel.completeOnboarding] persists the
 * `hasCompletedOnboarding = true` flag exactly once.
 *
 * The flag is read by `AppNavGraph` on the next cold start to decide
 * whether to route to onboarding — a regression where the flag is
 * never flipped would put the user into an onboarding loop, so this
 * test is the cheapest available guard. It also pins down that the VM
 * sets the **onboarding-completion** flag, not `isFirstLaunch` (which
 * `InitializeAppUseCase` already clears during cold-start init).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        coEvery { settingsRepository.setHasCompletedOnboarding(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given onboarding completion when invoked then sets hasCompletedOnboarding to true`() = runTest {
        val viewModel = OnboardingViewModel(settingsRepository)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        // The VM must not also touch `isFirstLaunch` — that flag is owned
        // by `InitializeAppUseCase` and resetting it would re-trigger
        // one-shot seeding (default prompts, the seeded pipeline).
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }
}
