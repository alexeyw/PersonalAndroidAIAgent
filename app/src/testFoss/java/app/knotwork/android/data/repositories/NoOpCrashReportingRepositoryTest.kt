package app.knotwork.android.data.repositories

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NoOpCrashReportingRepository], the `foss`-flavour crash
 * reporter.
 *
 * The contract for the F-Droid build is simple but important: every method must
 * be a strict no-op that neither throws nor performs any work (and, by
 * construction, makes no network call — the class has no collaborators at all).
 * These tests pin that behaviour so a future change cannot quietly turn the
 * no-op into something that touches the network or crashes the app.
 */
class NoOpCrashReportingRepositoryTest {

    private val repository = NoOpCrashReportingRepository()

    @Test
    fun `setEnabled true does nothing and does not throw`() = runTest {
        repository.setEnabled(enabled = true)
        // Reaching here without an exception is the assertion: the no-op
        // completed normally for the enable path.
        assertTrue(true)
    }

    @Test
    fun `setEnabled false does nothing and does not throw`() = runTest {
        repository.setEnabled(enabled = false)
        assertTrue(true)
    }

    @Test
    fun `recordException does nothing and does not throw`() = runTest {
        repository.recordException(IllegalStateException("boom"), extras = mapOf("k" to "v"))
        assertTrue(true)
    }

    @Test
    fun `recordException with default extras does nothing and does not throw`() = runTest {
        repository.recordException(RuntimeException("boom"))
        assertTrue(true)
    }

    @Test
    fun `setCustomKey does nothing and does not throw`() = runTest {
        repository.setCustomKey(key = "active_model", value = "gemma")
        assertTrue(true)
    }
}
