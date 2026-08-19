package app.knotwork.android.data.services

import android.content.Context
import app.knotwork.android.domain.constants.ExternalAutomationContract
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Robolectric coverage for [ExternalAutomationCallbackSender] — the outbound half
 * of the contract. Asserts the wire shape a third-party caller parses, and the
 * two properties that keep a misdirected callback harmless: it is package-directed
 * rather than component-explicit, and it carries no run content.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalAutomationCallbackSenderTest {

    private lateinit var context: Context
    private lateinit var sender: ExternalAutomationCallbackSender

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        sender = ExternalAutomationCallbackSender(context)
    }

    private fun sentIntents() = Shadows.shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents

    @Test
    fun `given a terminal status when notified then the caller gets its id and the status`() {
        sender.notifyOutcome(
            returnPackage = "com.example.caller",
            returnAction = ExternalAutomationContract.ACTION_RUN_RESULT,
            requestId = "req-1",
            status = ExternalAutomationStatus.Completed,
        )

        val intent = sentIntents().single()
        assertEquals(ExternalAutomationContract.ACTION_RUN_RESULT, intent.action)
        // Package-directed, never a ComponentName: the app cannot know the caller's
        // receiver class, and automation apps register theirs at runtime.
        assertEquals("com.example.caller", intent.`package`)
        assertNull(intent.component)
        assertEquals("req-1", intent.getStringExtra(ExternalAutomationContract.EXTRA_REQUEST_ID))
        assertEquals("Completed", intent.getStringExtra(ExternalAutomationContract.EXTRA_STATUS))
    }

    @Test
    fun `given a refusal when notified then the reason travels with it`() {
        sender.notifyOutcome(
            returnPackage = "com.example.caller",
            returnAction = ExternalAutomationContract.ACTION_RUN_RESULT,
            requestId = "req-1",
            status = ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED),
        )

        val intent = sentIntents().single()
        assertEquals("Rejected", intent.getStringExtra(ExternalAutomationContract.EXTRA_STATUS))
        assertEquals("CONTRACT_DISABLED", intent.getStringExtra(ExternalAutomationContract.EXTRA_STATUS_REASON))
    }

    @Test
    fun `given a non-refusal status when notified then no reason key is present at all`() {
        sender.notifyOutcome(
            returnPackage = "com.example.caller",
            returnAction = ExternalAutomationContract.ACTION_RUN_RESULT,
            requestId = "req-1",
            status = ExternalAutomationStatus.Accepted,
        )

        // "Key absent" and "key present and empty" are different statements; the
        // contract says the reason is present only for a refusal.
        assertFalse(sentIntents().single().hasExtra(ExternalAutomationContract.EXTRA_STATUS_REASON))
    }

    @Test
    fun `given every status when notified then the published wire names are used`() {
        val expected = mapOf(
            ExternalAutomationStatus.Accepted to "Accepted",
            ExternalAutomationStatus.Completed to "Completed",
            ExternalAutomationStatus.Failed to "Failed",
            ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_MISSING) to "Rejected",
            ExternalAutomationStatus.Blocked(ExternalAutomationRejectionReason.RATE_LIMITED) to "Blocked",
        )

        expected.forEach { (status, wireName) ->
            sender.notifyOutcome("com.example.caller", "a", "req", status)
            assertEquals(wireName, sentIntents().last().getStringExtra(ExternalAutomationContract.EXTRA_STATUS))
        }
    }

    @Test
    fun `given a callback that carries no run content when notified then only contract keys are present`() {
        sender.notifyOutcome("com.example.caller", "a", "req-1", ExternalAutomationStatus.Completed)

        val keys = sentIntents().single().extras?.keySet().orEmpty()
        assertEquals(
            setOf(ExternalAutomationContract.EXTRA_REQUEST_ID, ExternalAutomationContract.EXTRA_STATUS),
            keys,
        )
    }

    @Test
    fun `given the broadcast throws when notified then the failure is absorbed`() {
        val hostile = mockk<Context>()
        every { hostile.sendBroadcast(any()) } throws SecurityException("not allowed")

        // Delivery is a courtesy to a third-party app; nothing about the run depends
        // on it, so a caller that uninstalled itself cannot fail the run it started.
        ExternalAutomationCallbackSender(hostile).notifyOutcome(
            "com.example.gone",
            "a",
            "req-1",
            ExternalAutomationStatus.Completed,
        )
    }
}
