package app.knotwork.android.data.repositories

import app.knotwork.android.domain.models.ClarificationRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ClarificationRepositoryImpl].
 *
 * Cover: happy path (submit resolves the suspended request), default-answer behaviour
 * on timeout for both option-list and free-form requests, the visible
 * `pendingRequests` flow lifecycle, tolerance for stray submissions (unknown id,
 * submission before any request), and concurrent multi-request use where every
 * pending request must remain addressable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClarificationRepositoryImplTest {

    private lateinit var repository: ClarificationRepositoryImpl

    @Before
    fun setup() {
        repository = ClarificationRepositoryImpl()
    }

    @Test
    fun `given pending request when submitClarification called then requestAnswer returns submitted answer`() =
        runTest {
            val request = ClarificationRequest(
                id = "req-1",
                sessionId = "session-1",
                question = "Pick a colour",
                options = listOf("red", "blue"),
                timeoutMs = 60_000L,
            )

            val deferredAnswer = async { repository.requestAnswer(request) }
            // Allow requestAnswer to register the deferred and publish the request.
            yield()

            repository.submitClarification("req-1", "blue")

            assertEquals("blue", deferredAnswer.await())
        }

    @Test
    fun `given options non-empty and timeout elapses then returns first option`() = runTest {
        val request = ClarificationRequest(
            id = "req-2",
            sessionId = "session-1",
            question = "Pick a colour",
            options = listOf("red", "blue"),
            timeoutMs = 100L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        advanceTimeBy(150L)
        advanceUntilIdle()

        assertEquals("red", deferredAnswer.await())
    }

    @Test
    fun `given options is null and timeout elapses then returns empty string`() = runTest {
        val request = ClarificationRequest(
            id = "req-3",
            sessionId = "session-1",
            question = "Free-form question",
            options = null,
            timeoutMs = 100L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        advanceTimeBy(150L)
        advanceUntilIdle()

        assertEquals("", deferredAnswer.await())
    }

    @Test
    fun `given options is empty list and timeout elapses then returns empty string`() = runTest {
        val request = ClarificationRequest(
            id = "req-4",
            sessionId = "session-1",
            question = "Edge case",
            options = emptyList(),
            timeoutMs = 100L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        advanceTimeBy(150L)
        advanceUntilIdle()

        assertEquals("", deferredAnswer.await())
    }

    @Test
    fun `given requestAnswer in flight then pendingRequests exposes the request and clears after resolve`() = runTest {
        val request = ClarificationRequest(
            id = "req-5",
            sessionId = "session-1",
            question = "What is your name?",
            options = null,
            timeoutMs = 60_000L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        yield()

        assertEquals(listOf(request), repository.pendingRequests.first())

        repository.submitClarification("req-5", "Alice")
        deferredAnswer.await()

        assertEquals(emptyList<ClarificationRequest>(), repository.pendingRequests.first())
    }

    @Test
    fun `given two sequential requests when each gets its own answer then both resolve correctly`() = runTest {
        val firstRequest = ClarificationRequest(
            id = "req-6a",
            sessionId = "session-1",
            question = "Q1?",
            options = null,
            timeoutMs = 60_000L,
        )
        val firstAnswer = async { repository.requestAnswer(firstRequest) }
        yield()
        repository.submitClarification("req-6a", "answer-a")
        assertEquals("answer-a", firstAnswer.await())

        val secondRequest = ClarificationRequest(
            id = "req-6b",
            sessionId = "session-1",
            question = "Q2?",
            options = listOf("yes", "no"),
            timeoutMs = 60_000L,
        )
        val secondAnswer = async { repository.requestAnswer(secondRequest) }
        yield()
        repository.submitClarification("req-6b", "yes")
        assertEquals("yes", secondAnswer.await())
    }

    @Test
    fun `given mismatched requestId in submitClarification then waiting request is not resolved`() = runTest {
        val request = ClarificationRequest(
            id = "req-7",
            sessionId = "session-1",
            question = "Real question",
            options = listOf("a", "b"),
            timeoutMs = 100L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        yield()

        // Wrong id: must be a no-op, the suspended deferred is untouched.
        repository.submitClarification("totally-different-id", "ignored")
        advanceTimeBy(150L)
        advanceUntilIdle()

        // Falls through to default (first option) because the real request still timed out.
        assertEquals("a", deferredAnswer.await())
    }

    @Test
    fun `given submitClarification called before any request then call is silently dropped`() = runTest {
        // Must not throw and must not affect a later request.
        repository.submitClarification("ghost-id", "ghost-answer")

        val request = ClarificationRequest(
            id = "req-8",
            sessionId = "session-1",
            question = "After ghost",
            options = null,
            timeoutMs = 60_000L,
        )
        val deferredAnswer = async { repository.requestAnswer(request) }
        yield()
        repository.submitClarification("req-8", "real-answer")

        assertEquals("real-answer", deferredAnswer.await())
    }

    @Test
    fun `given pending request resolved by timeout then pendingRequests flow is reset to empty`() = runTest {
        val request = ClarificationRequest(
            id = "req-9",
            sessionId = "session-1",
            question = "Will time out",
            options = null,
            timeoutMs = 50L,
        )

        val deferredAnswer = launch { repository.requestAnswer(request) }
        yield()
        advanceTimeBy(100L)
        advanceUntilIdle()
        deferredAnswer.join()

        assertEquals(emptyList<ClarificationRequest>(), repository.pendingRequests.first())
    }

    @Test
    fun `given two concurrent requests then both are visible and each is answered independently`() = runTest {
        val first = ClarificationRequest(
            id = "concurrent-a",
            sessionId = "session-1",
            question = "Q-A?",
            options = listOf("a1", "a2"),
            timeoutMs = 60_000L,
        )
        val second = ClarificationRequest(
            id = "concurrent-b",
            sessionId = "session-1",
            question = "Q-B?",
            options = null,
            timeoutMs = 60_000L,
        )

        val firstAnswer = async { repository.requestAnswer(first) }
        val secondAnswer = async { repository.requestAnswer(second) }
        yield()

        // Both requests must remain visible to the UI: a newer request must not hide
        // an older one that is still awaiting an answer.
        val visible = repository.pendingRequests.first()
        assertEquals(2, visible.size)
        assertTrue(visible.containsAll(listOf(first, second)))

        // Answer them in reverse order to prove neither was masked.
        repository.submitClarification("concurrent-b", "free-form-reply")
        repository.submitClarification("concurrent-a", "a2")

        assertEquals("a2", firstAnswer.await())
        assertEquals("free-form-reply", secondAnswer.await())

        assertEquals(emptyList<ClarificationRequest>(), repository.pendingRequests.first())
    }

    @Test
    fun `submitClarification returns true when request was successfully delivered`() = runTest {
        val request = ClarificationRequest(
            id = "ret-true",
            sessionId = "session-1",
            question = "Q?",
            options = listOf("a", "b"),
            timeoutMs = 60_000L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        yield()

        val accepted = repository.submitClarification("ret-true", "a")

        assertTrue(accepted)
        assertEquals("a", deferredAnswer.await())
    }

    @Test
    fun `submitClarification returns false when requestId is unknown`() = runTest {
        val accepted = repository.submitClarification("never-existed", "ignored")
        assertFalse(accepted)
    }

    @Test
    fun `submitClarification returns false when request has already timed out`() = runTest {
        val request = ClarificationRequest(
            id = "ret-late",
            sessionId = "session-1",
            question = "Late?",
            options = listOf("default"),
            timeoutMs = 50L,
        )

        val deferredAnswer = async { repository.requestAnswer(request) }
        yield()
        // Drive the timeout to completion before submitting.
        advanceTimeBy(100L)
        advanceUntilIdle()
        // The pipeline coroutine has already received the default answer.
        assertEquals("default", deferredAnswer.await())

        val accepted = repository.submitClarification("ret-late", "too-late")

        assertFalse(accepted)
    }

    @Test
    fun `given two concurrent requests when one times out then the other remains answerable`() = runTest {
        val shortLived = ClarificationRequest(
            id = "short",
            sessionId = "session-1",
            question = "Will time out",
            options = listOf("default-short"),
            timeoutMs = 50L,
        )
        val longLived = ClarificationRequest(
            id = "long",
            sessionId = "session-1",
            question = "Stays around",
            options = listOf("default-long"),
            timeoutMs = 60_000L,
        )

        val shortAnswer = async { repository.requestAnswer(shortLived) }
        val longAnswer = async { repository.requestAnswer(longLived) }
        // Let both coroutines run up to their suspension point at the current time
        // without advancing virtual time (which would also fire the long timeout).
        runCurrent()

        // Trigger the short timeout but stay well below the long timeout.
        advanceTimeBy(100L)
        runCurrent()

        assertEquals("default-short", shortAnswer.await())

        // The long request must still be pending and answerable.
        val stillPending = repository.pendingRequests.first()
        assertEquals(listOf(longLived), stillPending)

        repository.submitClarification("long", "user-reply")
        assertEquals("user-reply", longAnswer.await())
    }
}
