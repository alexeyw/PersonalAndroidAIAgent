package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BuildDynamicShortcutsUseCase]: recency ordering, the count
 * cap, label clamping, and the blank-name / non-positive-cap guards.
 */
class BuildDynamicShortcutsUseCaseTest {

    private val useCase = BuildDynamicShortcutsUseCase()

    private fun session(id: String, name: String, updatedAt: Long) =
        ChatSession(id = id, name = name, updatedAt = updatedAt)

    @Test
    fun `given sessions when invoked then orders by recency and caps to maxCount`() {
        val sessions = listOf(
            session("a", "Oldest", updatedAt = 100),
            session("b", "Newest", updatedAt = 300),
            session("c", "Middle", updatedAt = 200),
        )

        val specs = useCase(sessions, maxCount = 2)

        assertEquals(listOf("b", "c"), specs.map { it.sessionId })
        assertEquals(listOf(0, 1), specs.map { it.rank })
    }

    @Test
    fun `given blank-named session when invoked then skips it`() {
        val sessions = listOf(
            session("a", "   ", updatedAt = 300),
            session("b", "Real", updatedAt = 200),
        )

        val specs = useCase(sessions, maxCount = 5)

        assertEquals(listOf("b"), specs.map { it.sessionId })
    }

    @Test
    fun `given long name when invoked then clamps short and long labels`() {
        val sessions = listOf(session("a", "A very long conversation title indeed", updatedAt = 1))

        val spec = useCase(sessions).single()

        assertTrue("short label too long: '${spec.shortLabel}'", spec.shortLabel.length <= 10)
        assertTrue("long label too long: '${spec.longLabel}'", spec.longLabel.length <= 25)
        assertTrue(spec.shortLabel.endsWith("…"))
    }

    @Test
    fun `given short name when invoked then keeps it verbatim`() {
        val spec = useCase(listOf(session("a", "Hi", updatedAt = 1))).single()

        assertEquals("Hi", spec.shortLabel)
        assertEquals("Hi", spec.longLabel)
        assertEquals("session_a", spec.id)
    }

    @Test
    fun `given non-positive maxCount when invoked then returns empty`() {
        val specs = useCase(listOf(session("a", "Real", updatedAt = 1)), maxCount = 0)

        assertTrue(specs.isEmpty())
    }
}
