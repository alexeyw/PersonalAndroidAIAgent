package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.isInteractive
import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunCeilingLimit
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ResolveRunCeilingsUseCase] — which configured numbers apply to
 * a run, decided from its origin.
 *
 * The behaviour worth pinning is that the interactive/background split follows
 * `RunOrigin.isInteractive` rather than a second, hand-maintained list: a new
 * background surface must not be able to arrive with interactive allowances
 * simply because somebody forgot to add it here.
 */
class ResolveRunCeilingsUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ResolveRunCeilingsUseCase

    @Before
    fun setUp() {
        settingsRepository = mockk()
        every { settingsRepository.pipelineMaxSteps } returns flowOf(INTERACTIVE_STEPS)
        every { settingsRepository.pipelineMaxStepsBackground } returns flowOf(BACKGROUND_STEPS)
        every { settingsRepository.runMaxTokens } returns flowOf(INTERACTIVE_TOKENS)
        every { settingsRepository.runMaxTokensBackground } returns flowOf(BACKGROUND_TOKENS)
        useCase = ResolveRunCeilingsUseCase(settingsRepository)
    }

    @Test
    fun `given an interactive origin then the interactive numbers apply`() = runTest {
        val ceilings = useCase(RunOrigin.CHAT)

        assertEquals(INTERACTIVE_STEPS, ceilings.steps.hard)
        assertEquals(INTERACTIVE_TOKENS, ceilings.tokens.hard)
        assertEquals(RunOrigin.CHAT, ceilings.origin)
    }

    @Test
    fun `given a background origin then the conservative numbers apply`() = runTest {
        val ceilings = useCase(RunOrigin.TRIGGER)

        assertEquals(BACKGROUND_STEPS, ceilings.steps.hard)
        assertEquals(BACKGROUND_TOKENS, ceilings.tokens.hard)
    }

    @Test
    fun `given every origin then the split matches isInteractive exactly`() = runTest {
        // This asserts the *coupling*, not the partition: it fails if the use
        // case stops deriving the split from `isInteractive` and hardcodes a
        // list of its own. The partition itself is pinned by the explicit test
        // below, which names the four background origins rather than deriving
        // them.
        RunOrigin.entries.forEach { origin ->
            val ceilings = useCase(origin)
            val expectedSteps = if (origin.isInteractive) INTERACTIVE_STEPS else BACKGROUND_STEPS
            val expectedTokens = if (origin.isInteractive) INTERACTIVE_TOKENS else BACKGROUND_TOKENS
            assertEquals("steps for $origin", expectedSteps, ceilings.steps.hard)
            assertEquals("tokens for $origin", expectedTokens, ceilings.tokens.hard)
        }
    }

    @Test
    fun `given the surfaces nobody watches then all four get the background numbers`() = runTest {
        // Named explicitly rather than derived, so that reclassifying one of
        // these as interactive is a deliberate act with a failing test, not a
        // side effect of editing a `when` somewhere else.
        listOf(RunOrigin.SCHEDULER, RunOrigin.QUICK_TILE, RunOrigin.TRIGGER, RunOrigin.EXTERNAL)
            .forEach { origin ->
                assertEquals("steps for $origin", BACKGROUND_STEPS, useCase(origin).steps.hard)
            }
    }

    @Test
    fun `given any origin then the soft threshold sits below the hard one`() = runTest {
        RunOrigin.entries.forEach { origin ->
            val ceilings = useCase(origin)
            assertTrue("steps for $origin", ceilings.steps.soft < ceilings.steps.hard)
            assertTrue("tokens for $origin", ceilings.tokens.soft < ceilings.tokens.hard)
        }
    }

    @Test
    fun `given any origin then the money axis is declared unavailable`() = runTest {
        // The app is bring-your-own-key and has no price source. A ceiling that
        // claimed to bound spend while measuring nothing would be worse than
        // none, so "unavailable" is a value the product can render.
        RunOrigin.entries.forEach { origin ->
            assertEquals(
                "money for $origin",
                RunCeilingLimit.Unavailable,
                useCase(origin).limitOn(RunCeilingAxis.MONEY),
            )
        }
    }

    private companion object {
        const val INTERACTIVE_STEPS = 40
        const val BACKGROUND_STEPS = 12
        const val INTERACTIVE_TOKENS = 900_000
        const val BACKGROUND_TOKENS = 90_000
    }
}
