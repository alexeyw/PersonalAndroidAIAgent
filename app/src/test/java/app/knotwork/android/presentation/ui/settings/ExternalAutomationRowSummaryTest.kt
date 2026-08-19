package app.knotwork.android.presentation.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure half of the external-automation Background rows.
 *
 * [isExternalAutomationUnbound] decides whether the binding row wears its warning
 * treatment, and the warning is the only thing that separates "you have not set
 * this up yet" from "this is switched on and refusing everything that arrives" —
 * two states that otherwise render identically as `Not set`.
 */
class ExternalAutomationRowSummaryTest {

    private fun state(
        enabled: Boolean,
        boundId: String? = null,
        pipelines: List<PipelineBindingOption> = emptyList(),
    ) = SettingsUiState(
        externalAutomationEnabled = enabled,
        externalAutomationPipelineId = boundId,
        bindablePipelines = pipelines,
    )

    private val library = listOf(PipelineBindingOption("p1", "Morning digest"))

    @Test
    fun `given the contract switched off when asked then nothing is flagged`() {
        // Off and unbound is the shipped default, not a problem to warn about.
        assertFalse(state(enabled = false).isExternalAutomationUnbound())
        assertFalse(state(enabled = false, boundId = "p1", pipelines = library).isExternalAutomationUnbound())
    }

    @Test
    fun `given the contract switched on with no binding when asked then it is flagged`() {
        assertTrue(state(enabled = true).isExternalAutomationUnbound())
    }

    @Test
    fun `given a resolvable binding when asked then nothing is flagged`() {
        assertFalse(state(enabled = true, boundId = "p1", pipelines = library).isExternalAutomationUnbound())
    }

    @Test
    fun `given a binding whose pipeline was deleted when asked then it is flagged`() {
        // The id outlives the pipeline; the authorizer cannot honour it, so the
        // surface is on and inert exactly as if nothing had ever been picked.
        val survivors = listOf(PipelineBindingOption("p2", "Expense report"))
        assertTrue(state(enabled = true, boundId = "p1", pipelines = survivors).isExternalAutomationUnbound())
    }

    @Test
    fun `given the pipeline list has not loaded yet when asked then it is not flagged`() {
        // An empty library is indistinguishable from an unread one, so the row
        // stays quiet rather than flashing a warning on every screen open.
        assertFalse(state(enabled = true, boundId = "p1").isExternalAutomationUnbound())
    }
}
