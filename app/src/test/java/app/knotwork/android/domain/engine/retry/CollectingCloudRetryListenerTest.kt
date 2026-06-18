package app.knotwork.android.domain.engine.retry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CollectingCloudRetryListener] — the buffer that lets the cloud
 * node executor drain retries into console lines after the call completes.
 */
class CollectingCloudRetryListenerTest {

    @Test
    fun `given retries when reported then buffered in order with formatted messages`() {
        val listener = CollectingCloudRetryListener()

        listener.onRetry(provider = "openai", attempt = 1, maxRetries = 2)
        listener.onRetry(provider = "openai", attempt = 2, maxRetries = 2)

        assertEquals(2, listener.attempts.size)
        assertEquals("Cloud retry 1/2 for openai", listener.attempts[0].consoleMessage())
        assertEquals("Cloud retry 2/2 for openai", listener.attempts[1].consoleMessage())
    }

    @Test
    fun `given no retries when read then empty`() {
        assertEquals(0, CollectingCloudRetryListener().attempts.size)
    }
}
