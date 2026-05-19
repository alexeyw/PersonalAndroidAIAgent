package app.knotwork.design.components.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-Kotlin coverage for [resolveDragOutcome] — the snap-cycle helper
 * that translates an accumulated vertical-drag distance on the console
 * pane's handle strip into a snap change or a full dismiss. Pulled out
 * of the Composable so the gesture behaviour is verifiable without
 * Robolectric / Compose tooling.
 */
class ConsoleDragOutcomeTest {

    private val threshold = 24f

    @Test
    fun `drag down past threshold from Full collapses to Partial`() {
        var newSnap: ConsoleSnap? = null
        var closed = false
        resolveDragOutcome(
            snap = ConsoleSnap.Full,
            accumulated = threshold + 1f,
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = { closed = true },
        )
        assertEquals(ConsoleSnap.Partial, newSnap)
        assertEquals(false, closed)
    }

    @Test
    fun `drag down past threshold from Partial collapses to Peek`() {
        var newSnap: ConsoleSnap? = null
        resolveDragOutcome(
            snap = ConsoleSnap.Partial,
            accumulated = threshold + 1f,
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = {},
        )
        assertEquals(ConsoleSnap.Peek, newSnap)
    }

    @Test
    fun `drag down past threshold from Peek dismisses the overlay`() {
        var closed = false
        var newSnap: ConsoleSnap? = null
        resolveDragOutcome(
            snap = ConsoleSnap.Peek,
            accumulated = threshold + 1f,
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = { closed = true },
        )
        assertEquals(true, closed)
        assertNull(newSnap)
    }

    @Test
    fun `drag up past threshold from Peek expands to Partial`() {
        var newSnap: ConsoleSnap? = null
        resolveDragOutcome(
            snap = ConsoleSnap.Peek,
            accumulated = -(threshold + 1f),
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = {},
        )
        assertEquals(ConsoleSnap.Partial, newSnap)
    }

    @Test
    fun `drag up past threshold from Partial expands to Full`() {
        var newSnap: ConsoleSnap? = null
        resolveDragOutcome(
            snap = ConsoleSnap.Partial,
            accumulated = -(threshold + 1f),
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = {},
        )
        assertEquals(ConsoleSnap.Full, newSnap)
    }

    @Test
    fun `drag up past threshold from Full is a no-op`() {
        var newSnap: ConsoleSnap? = null
        var closed = false
        resolveDragOutcome(
            snap = ConsoleSnap.Full,
            accumulated = -(threshold + 1f),
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = { closed = true },
        )
        assertNull(newSnap)
        assertEquals(false, closed)
    }

    @Test
    fun `drag under threshold in either direction is ignored`() {
        var newSnap: ConsoleSnap? = null
        var closed = false
        resolveDragOutcome(
            snap = ConsoleSnap.Partial,
            accumulated = threshold - 1f,
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = { closed = true },
        )
        resolveDragOutcome(
            snap = ConsoleSnap.Partial,
            accumulated = -(threshold - 1f),
            thresholdPx = threshold,
            onSnapChange = { newSnap = it },
            onCloseConsole = { closed = true },
        )
        assertNull(newSnap)
        assertEquals(false, closed)
    }
}
