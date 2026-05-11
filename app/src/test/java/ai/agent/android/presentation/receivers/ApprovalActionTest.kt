package ai.agent.android.presentation.receivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ApprovalAction]: round-trip parsing and rejection of unknown actions.
 */
class ApprovalActionTest {

    @Test
    fun `given each enum value when its action is parsed then the same value is returned`() {
        ApprovalAction.entries.forEach { value ->
            assertEquals(value, ApprovalAction.fromAction(value.action))
        }
    }

    @Test
    fun `given unknown action when fromAction is called then null is returned`() {
        assertNull(ApprovalAction.fromAction("ai.agent.android.ACTION_UNKNOWN"))
    }

    @Test
    fun `given null when fromAction is called then null is returned`() {
        assertNull(ApprovalAction.fromAction(null))
    }
}
