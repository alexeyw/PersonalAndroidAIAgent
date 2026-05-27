package ai.agent.android.presentation.receivers

import ai.agent.android.domain.constants.TimeAndIdConstants
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.presentation.notifications.ApprovalNotificationManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Robolectric coverage for [AgentApprovalReceiver] — the broadcast receiver that
 * translates the Approve / Deny notification action buttons into
 * `AgentOrchestratorUseCase.resumeWithApproval` calls.
 *
 * The receiver is `@AndroidEntryPoint`; rather than spin up a full
 * `HiltTestApplication` runner just to instantiate one receiver, we flip the
 * private `injected` flag in the generated `Hilt_AgentApprovalReceiver`
 * superclass via reflection and assign the `@Inject lateinit var` field by
 * hand (same pattern used by `AgentForegroundServiceTest`). The end result is
 * a receiver whose `onReceive` runs the production code path unmodified
 * against a mocked orchestrator.
 */
@RunWith(RobolectricTestRunner::class)
class AgentApprovalReceiverTest {

    private lateinit var context: Context
    private lateinit var orchestrator: AgentOrchestratorUseCase
    private lateinit var receiver: AgentApprovalReceiver

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        orchestrator = mockk(relaxed = true)
        receiver = AgentApprovalReceiver().also { instance ->
            // Bypass Hilt's auto-inject — flip the generated `injected` flag and assign
            // the `@Inject lateinit var` directly. See class KDoc for rationale.
            val injectedField = instance.javaClass.superclass!!.getDeclaredField("injected")
            injectedField.isAccessible = true
            injectedField.setBoolean(instance, true)
            instance.orchestratorUseCase = orchestrator
        }
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun expectedNotificationId(sessionId: String): Int = ApprovalNotificationManager.NOTIFICATION_ID +
        sessionId.hashCode() % TimeAndIdConstants.NOTIFICATION_ID_RANGE

    /** Posts a placeholder notification at the slot the receiver is expected to cancel. */
    private fun postPlaceholder(sessionId: String) {
        val notification = Notification.Builder(context, "test-channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("placeholder")
            .build()
        notificationManager().notify(expectedNotificationId(sessionId), notification)
    }

    private fun intent(action: String?, sessionId: String?): Intent = Intent().apply {
        if (action != null) this.action = action
        if (sessionId != null) putExtra("sessionId", sessionId)
    }

    @Test
    fun `given APPROVE action when onReceive then resumeWithApproval is called with true`() {
        receiver.onReceive(context, intent(ApprovalAction.APPROVE.action, "s1"))

        verify(exactly = 1) { orchestrator.resumeWithApproval("s1", true) }
        confirmVerified(orchestrator)
    }

    @Test
    fun `given DENY action when onReceive then resumeWithApproval is called with false`() {
        receiver.onReceive(context, intent(ApprovalAction.DENY.action, "s1"))

        verify(exactly = 1) { orchestrator.resumeWithApproval("s1", false) }
        confirmVerified(orchestrator)
    }

    @Test
    fun `given unknown action when onReceive then orchestrator is not called`() {
        receiver.onReceive(context, intent("ai.agent.android.ACTION_UNKNOWN", "s1"))

        // Unknown actions hit the `null -> Unit` branch; the receiver still
        // dismisses the notification but must not advance the orchestrator.
        verify(exactly = 0) { orchestrator.resumeWithApproval(any(), any()) }
        confirmVerified(orchestrator)
    }

    @Test
    fun `given null action when onReceive then orchestrator is not called`() {
        receiver.onReceive(context, intent(action = null, sessionId = "s1"))

        verify(exactly = 0) { orchestrator.resumeWithApproval(any(), any()) }
        confirmVerified(orchestrator)
    }

    @Test
    fun `given missing sessionId extra when onReceive then orchestrator skipped and notification kept`() {
        // Post a placeholder at a slot that *would* be cancelled if the receiver
        // continued past the sessionId guard — used to prove the early return.
        postPlaceholder("would-be-cancelled")
        assertEquals(1, Shadows.shadowOf(notificationManager()).size())

        receiver.onReceive(context, intent(ApprovalAction.APPROVE.action, sessionId = null))

        verify(exactly = 0) { orchestrator.resumeWithApproval(any(), any()) }
        confirmVerified(orchestrator)
        // Pre-existing notification must still be there — the receiver returned
        // before reaching the cancel call.
        assertEquals(1, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given a pending notification when onReceive APPROVE then it is cancelled at the matching id`() {
        val sessionId = "session-cancel"
        postPlaceholder(sessionId)
        assertEquals(
            "Precondition: placeholder notification must be posted before onReceive runs",
            1,
            Shadows.shadowOf(notificationManager()).size(),
        )

        receiver.onReceive(context, intent(ApprovalAction.APPROVE.action, sessionId))

        // Notification at the documented partitioned slot must be cleared.
        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
        verify(exactly = 1) { orchestrator.resumeWithApproval(sessionId, true) }
    }

    @Test
    fun `given an unknown action and a pending notification when onReceive then notification is still cancelled`() {
        // Documents current behaviour: notification cancellation happens before the
        // action-dispatch `when`, so even an unknown action clears the slot. This
        // is intentional — once the user has tapped the notification at all, the
        // shade entry is consumed.
        val sessionId = "session-cancel"
        postPlaceholder(sessionId)

        receiver.onReceive(context, intent("ai.agent.android.ACTION_UNKNOWN", sessionId))

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
        verify(exactly = 0) { orchestrator.resumeWithApproval(any(), any()) }
    }

    @Test
    fun `given two APPROVE intents in a row when onReceive then resumeWithApproval is invoked twice`() {
        // Documents that the receiver has no internal dedup — repeated broadcasts
        // forward through. Dedup, if needed, is the orchestrator's responsibility.
        receiver.onReceive(context, intent(ApprovalAction.APPROVE.action, "s1"))
        receiver.onReceive(context, intent(ApprovalAction.APPROVE.action, "s1"))

        verify(exactly = 2) { orchestrator.resumeWithApproval("s1", true) }
    }
}
