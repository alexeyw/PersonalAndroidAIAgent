package app.knotwork.android.domain.engine.structured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CollectingRepairListener].
 */
class CollectingRepairListenerTest {

    @Test
    fun `given no attempts when read then attempts is empty`() {
        assertTrue(CollectingRepairListener().attempts.isEmpty())
    }

    @Test
    fun `given attempts recorded when read then they are returned in order`() {
        val listener = CollectingRepairListener()

        listener.onRepairAttempt("Decompose", attempt = 1, maxRepairs = 2)
        listener.onRepairAttempt("Decompose", attempt = 2, maxRepairs = 2)

        val attempts = listener.attempts
        assertEquals(2, attempts.size)
        assertEquals(CollectingRepairListener.RepairAttempt("Decompose", 1, 2), attempts[0])
        assertEquals(CollectingRepairListener.RepairAttempt("Decompose", 2, 2), attempts[1])
    }

    @Test
    fun `given an attempt when consoleMessage then renders the progress fraction`() {
        val attempt = CollectingRepairListener.RepairAttempt("Router", attempt = 1, maxRepairs = 3)

        assertEquals("Output repair 1/3 for node Router", attempt.consoleMessage())
    }
}
