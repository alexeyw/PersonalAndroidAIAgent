package app.knotwork.android.presentation.ui.splash

import app.knotwork.design.screens.splash.SplashViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the `SplashUiState.toViewState(...)` mapper that bridges the
 * app-side cold-start projection to the catalog `SplashViewState` consumed
 * by `SplashContent`.
 */
class SplashStateMappingTest {

    private val defaultMessage = "Starting…"

    @Test
    fun `given pristine ui state when mapped then Initializing`() {
        val state = SplashUiState()

        val result = state.toViewState(defaultMessage = defaultMessage)

        assertEquals(SplashViewState.Initializing, result)
    }

    @Test
    fun `given in-flight progress when mapped then Loading with real message`() {
        val state = SplashUiState(
            message = "Loading model",
            progressFraction = 0.42f,
        )

        val result = state.toViewState(defaultMessage = defaultMessage)

        assertTrue(result is SplashViewState.Loading)
        result as SplashViewState.Loading
        assertEquals("Loading model", result.message)
        assertEquals(0.42f, result.progress, 0.001f)
    }

    @Test
    fun `given default message but non-zero progress when mapped then Loading with localised default`() {
        val state = SplashUiState(progressFraction = 0.10f)

        val result = state.toViewState(defaultMessage = defaultMessage)

        assertTrue(result is SplashViewState.Loading)
        assertEquals(defaultMessage, (result as SplashViewState.Loading).message)
    }

    @Test
    fun `given out-of-range progress when mapped then coerced into 0_1f`() {
        val state = SplashUiState(message = "x", progressFraction = 2f)

        val result = state.toViewState(defaultMessage = defaultMessage)

        assertEquals(1f, (result as SplashViewState.Loading).progress, 0.001f)
    }

    @Test
    fun `given error message when mapped then Error wins over loading`() {
        val state = SplashUiState(
            message = "still progressing",
            progressFraction = 0.8f,
            errorMessage = "model file missing",
        )

        val result = state.toViewState(defaultMessage = defaultMessage)

        assertTrue(result is SplashViewState.Error)
        assertEquals("model file missing", (result as SplashViewState.Error).message)
    }
}
