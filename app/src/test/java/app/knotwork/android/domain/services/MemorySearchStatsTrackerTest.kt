package app.knotwork.android.domain.services

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemorySearchStatsTracker] — the session-scoped rolling
 * window behind the Settings AVG SCORE stat cell.
 */
class MemorySearchStatsTrackerTest {

    private lateinit var tracker: MemorySearchStatsTracker

    @Before
    fun setup() {
        tracker = MemorySearchStatsTracker()
    }

    @Test
    fun `given no recorded search when averageScore collected then emits null`() = runTest {
        assertNull(tracker.averageScore.first())
    }

    @Test
    fun `given one recorded search when averageScore collected then emits the mean`() = runTest {
        tracker.record(listOf(0.8f, 0.6f))

        assertEquals(0.7f, tracker.averageScore.first()!!, 0.0001f)
    }

    @Test
    fun `given more than five scores in one call when recorded then only the strongest five count`() = runTest {
        // Ranked best-first, as findSimilarMemories returns them: only the
        // head (5 scores of 1.0) must enter the window; the low tail is cut.
        tracker.record(List(5) { 1.0f } + List(20) { 0.0f })

        assertEquals(1.0f, tracker.averageScore.first()!!, 0.0001f)
    }

    @Test
    fun `given more than thirty-two observations when recorded then only the latest window counts`() = runTest {
        // 7 calls × 5 scores of 0.0 fill the 32-slot window…
        repeat(7) { tracker.record(List(5) { 0.0f }) }
        // …then 32 / 5 = 7 calls of 1.0 fully evict them.
        repeat(7) { tracker.record(List(5) { 1.0f }) }

        // 32-slot window now holds 32 × 1.0 (the 0.0 era fully evicted).
        assertEquals(1.0f, tracker.averageScore.first()!!, 0.0001f)
    }

    @Test
    fun `given empty score list when recorded then it is a no-op`() = runTest {
        tracker.record(emptyList())

        assertNull(tracker.averageScore.first())
    }

    @Test
    fun `given recorded scores when reset then averageScore reverts to null`() = runTest {
        tracker.record(listOf(0.9f))

        tracker.reset()

        assertNull(tracker.averageScore.first())
    }
}
