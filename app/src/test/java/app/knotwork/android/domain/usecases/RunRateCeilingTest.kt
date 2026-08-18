package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.RunOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RunRateCeiling] — the shared runaway-guard arithmetic.
 *
 * The boundary is asserted explicitly because the guard is an inequality that is
 * easy to write one off: at exactly the limit the next run must already be
 * refused, since the limit counts runs that have *started*.
 */
class RunRateCeilingTest {

    private val ceiling = RunRateCeiling(origin = RunOrigin.EXTERNAL, limitPerWindow = 3, windowMillis = 1_000L)

    @Test
    fun `given a count below the limit when checking then the run is allowed`() {
        assertFalse(ceiling.isExceededBy(0))
        assertFalse(ceiling.isExceededBy(2))
    }

    @Test
    fun `given a count at the limit when checking then the run is refused`() {
        assertTrue(ceiling.isExceededBy(3))
    }

    @Test
    fun `given a count above the limit when checking then the run is refused`() {
        assertTrue(ceiling.isExceededBy(4))
    }

    @Test
    fun `given a moment when asked for the window start then it is one window earlier`() {
        assertEquals(9_000L, ceiling.windowStart(10_000L))
    }

    @Test
    fun `given the shipped ceilings then external work gets no larger allowance than the app's own`() {
        // An external caller must not be able to start runs faster than the
        // agent's own scheduling tool is allowed to.
        assertTrue(RunRateCeiling.EXTERNAL.limitPerWindow <= RunRateCeiling.SCHEDULED.limitPerWindow)
        assertEquals(RunRateCeiling.ONE_HOUR_MILLIS, RunRateCeiling.EXTERNAL.windowMillis)
        assertEquals(RunRateCeiling.ONE_HOUR_MILLIS, RunRateCeiling.SCHEDULED.windowMillis)
    }

    @Test
    fun `given the shipped ceilings then each governs its own origin`() {
        assertEquals(RunOrigin.SCHEDULER, RunRateCeiling.SCHEDULED.origin)
        assertEquals(RunOrigin.EXTERNAL, RunRateCeiling.EXTERNAL.origin)
    }
}
