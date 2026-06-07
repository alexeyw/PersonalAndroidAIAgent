package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolApprovalPolicyTest {

    @Test
    fun `fromKey resolves wire keys`() {
        assertEquals(ToolApprovalPolicy.AllCalls, ToolApprovalPolicy.fromKey("all_calls"))
        assertEquals(ToolApprovalPolicy.SensitiveOrDestructive, ToolApprovalPolicy.fromKey("sensitive_or_destructive"))
        assertEquals(ToolApprovalPolicy.NeverPrompt, ToolApprovalPolicy.fromKey("never_prompt"))
    }

    @Test
    fun `fromKey returns DEFAULT for unknown values`() {
        assertEquals(ToolApprovalPolicy.DEFAULT, ToolApprovalPolicy.fromKey("bogus"))
        assertEquals(ToolApprovalPolicy.DEFAULT, ToolApprovalPolicy.fromKey(null))
    }

    @Test
    fun `DEFAULT is SensitiveOrDestructive`() {
        assertEquals(ToolApprovalPolicy.SensitiveOrDestructive, ToolApprovalPolicy.DEFAULT)
    }
}
